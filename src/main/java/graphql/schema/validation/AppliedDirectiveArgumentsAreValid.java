package graphql.schema.validation;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.NonNullableValueCoercedAsNullException;
import graphql.execution.ValuesResolver;
import graphql.language.Value;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.InputValueWithState;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.validation.ValidationUtil;

import java.util.Locale;

import static java.lang.String.format;

@Internal
public class AppliedDirectiveArgumentsAreValid extends GraphQLTypeVisitorStub {

    private ValidationUtil validationUtil = new ValidationUtil();


    @Override
    public TraversalControl visitGraphQLDirective(GraphQLDirective directive, TraverserContext<GraphQLSchemaElement> context) {
        // if there is no parent it means it is just a directive definition and not an applied directive
        if (context.getParentNode() != null) {
            for (GraphQLArgument graphQLArgument : directive.getArguments()) {
                checkArgument(
                        directive.getName(),
                        graphQLArgument.getName(),
                        graphQLArgument.getArgumentValue(),
                        graphQLArgument.getType(),
                        context
                );
            }
        }
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLAppliedDirective(GraphQLAppliedDirective directive, TraverserContext<GraphQLSchemaElement> context) {
        // if there is no parent it means it is just a directive definition and not an applied directive
        if (context.getParentNode() != null) {
            for (GraphQLAppliedDirectiveArgument graphQLArgument : directive.getArguments()) {
                checkArgument(
                        directive.getName(),
                        graphQLArgument.getName(),
                        graphQLArgument.getArgumentValue(),
                        graphQLArgument.getType(),
                        context
                );
            }
        }
        return TraversalControl.CONTINUE;
    }

    private void checkArgument(
            String directiveName,
            String argumentName,
            InputValueWithState argumentValue,
            GraphQLInputType argumentType,
            TraverserContext<GraphQLSchemaElement> context
    ) {
        GraphQLSchema schema = context.getVarFromParents(GraphQLSchema.class);
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        boolean invalid = false;
        if (argumentValue.isLiteral() &&
                !validationUtil.isValidLiteralValue((Value<?>) argumentValue.getValue(), argumentType, schema, GraphQLContext.getDefault(), Locale.getDefault())) {
            invalid = true;
        } else if (argumentValue.isExternal() &&
                !isValidExternalValue(schema, argumentValue.getValue(), argumentType, GraphQLContext.getDefault(), Locale.getDefault())) {
            invalid = true;
        } else if (argumentValue.isNotSet() && GraphQLTypeUtil.isNonNull(argumentType))  {
            invalid = true;
        }
        if (invalid) {
            String message = format("Invalid argument '%s' for applied directive of name '%s'", argumentName, directiveName);
            errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.InvalidAppliedDirectiveArgument, message));
        }
    }

    private boolean isValidExternalValue(GraphQLSchema schema, Object externalValue, GraphQLInputType type, GraphQLContext graphQLContext, Locale locale) {
        try {
            ValuesResolver.externalValueToInternalValue(schema.getCodeRegistry().getFieldVisibility(), externalValue, type, graphQLContext, locale);
            return true;
        } catch (CoercingParseValueException | NonNullableValueCoercedAsNullException e) {
            return false;
        }
    }
}
