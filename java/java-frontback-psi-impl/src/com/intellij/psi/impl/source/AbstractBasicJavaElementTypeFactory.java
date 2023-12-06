// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public abstract class AbstractBasicJavaElementTypeFactory {

  public static class JavaElementTypeContainer {

    public final IElementType ANNOTATION_PARAMETER_LIST;

    public final IElementType EXTENDS_LIST;

    public final IElementType IMPLEMENTS_LIST;

    public final IElementType FIELD;

    public final IElementType ENUM_CONSTANT;

    public final IElementType METHOD;

    public final IElementType ANNOTATION_METHOD;

    public final IElementType CLASS_INITIALIZER;

    public final IElementType PARAMETER;

    public final IElementType PARAMETER_LIST;

    public final IElementType EXTENDS_BOUND_LIST;

    public final IElementType THROWS_LIST;

    public final IElementType LAMBDA_EXPRESSION;

    public final IElementType METHOD_REF_EXPRESSION;

    public final IElementType MODULE;

    public final IElementType REQUIRES_STATEMENT;

    public final IElementType EXPORTS_STATEMENT;

    public final IElementType OPENS_STATEMENT;

    public final IElementType USES_STATEMENT;

    public final IElementType PROVIDES_STATEMENT;

    public final IElementType PROVIDES_WITH_LIST;

    public final IElementType RECORD_COMPONENT;

    public final IElementType RECORD_HEADER;

    public final IElementType PERMITS_LIST;

    public final IElementType CLASS;
    public final IElementType IMPLICIT_CLASS;

    public final IElementType ANONYMOUS_CLASS;

    public final IElementType ENUM_CONSTANT_INITIALIZER;

    public final IElementType TYPE_PARAMETER_LIST;

    public final IElementType TYPE_PARAMETER;

    public final IElementType IMPORT_LIST;

    public final IElementType IMPORT_STATEMENT;

    public final IElementType IMPORT_STATIC_STATEMENT;

    public final IElementType MODIFIER_LIST;

    public final IElementType ANNOTATION;

    public final IElementType NAME_VALUE_PAIR;

    public final IElementType LITERAL_EXPRESSION;

    public final IElementType IMPORT_STATIC_REFERENCE;

    public final IElementType TYPE;

    public final IElementType DIAMOND_TYPE;

    public final IElementType REFERENCE_PARAMETER_LIST;

    public final IElementType JAVA_CODE_REFERENCE;

    public final IElementType PACKAGE_STATEMENT;

    public final IElementType LOCAL_VARIABLE;

    public final IElementType REFERENCE_EXPRESSION;

    public final IElementType THIS_EXPRESSION;

    public final IElementType SUPER_EXPRESSION;

    public final IElementType PARENTH_EXPRESSION;

    public final IElementType METHOD_CALL_EXPRESSION;

    public final IElementType TYPE_CAST_EXPRESSION;

    public final IElementType PREFIX_EXPRESSION;

    public final IElementType POSTFIX_EXPRESSION;

    public final IElementType BINARY_EXPRESSION;

    public final IElementType POLYADIC_EXPRESSION;

    public final IElementType CONDITIONAL_EXPRESSION;

    public final IElementType ASSIGNMENT_EXPRESSION;

    public final IElementType NEW_EXPRESSION;

    public final IElementType ARRAY_ACCESS_EXPRESSION;

    public final IElementType ARRAY_INITIALIZER_EXPRESSION;

    public final IElementType INSTANCE_OF_EXPRESSION;

    public final IElementType CLASS_OBJECT_ACCESS_EXPRESSION;

    public final IElementType EMPTY_EXPRESSION;
    public final IElementType TEMPLATE_EXPRESSION;

    public final IElementType TEMPLATE;

    public final IElementType EXPRESSION_LIST;

    public final IElementType EMPTY_STATEMENT;

    public final IElementType BLOCK_STATEMENT;

    public final IElementType EXPRESSION_STATEMENT;

    public final IElementType EXPRESSION_LIST_STATEMENT;

    public final IElementType DECLARATION_STATEMENT;

    public final IElementType IF_STATEMENT;

    public final IElementType WHILE_STATEMENT;

    public final IElementType FOR_STATEMENT;

    public final IElementType FOREACH_STATEMENT;

    public final IElementType FOREACH_PATTERN_STATEMENT;

    public final IElementType DO_WHILE_STATEMENT;

    public final IElementType SWITCH_STATEMENT;

    public final IElementType SWITCH_EXPRESSION;

    public final IElementType SWITCH_LABEL_STATEMENT;

    public final IElementType SWITCH_LABELED_RULE;

    public final IElementType BREAK_STATEMENT;

    public final IElementType YIELD_STATEMENT;

    public final IElementType CONTINUE_STATEMENT;

    public final IElementType RETURN_STATEMENT;

    public final IElementType THROW_STATEMENT;

    public final IElementType SYNCHRONIZED_STATEMENT;

    public final IElementType TRY_STATEMENT;

    public final IElementType RESOURCE_LIST;

    public final IElementType RESOURCE_VARIABLE;

    public final IElementType RESOURCE_EXPRESSION;

    public final IElementType CATCH_SECTION;

    public final IElementType LABELED_STATEMENT;

    public final IElementType ASSERT_STATEMENT;

    public final IElementType ANNOTATION_ARRAY_INITIALIZER;

    public final IElementType RECEIVER_PARAMETER;

    public final IElementType MODULE_REFERENCE;

    public final IElementType TYPE_TEST_PATTERN;
    public final IElementType UNNAMED_PATTERN;

    public final IElementType PATTERN_VARIABLE;

    public final IElementType DECONSTRUCTION_PATTERN;

    public final IElementType DECONSTRUCTION_LIST;

    public final IElementType DECONSTRUCTION_PATTERN_VARIABLE;

    public final IElementType PARENTHESIZED_PATTERN;

    public final IElementType DEFAULT_CASE_LABEL_ELEMENT;

    public final IElementType CASE_LABEL_ELEMENT_LIST;

    public final IElementType CODE_BLOCK;

    public final IElementType MEMBERS;

    public final IElementType STATEMENTS;

    public final IElementType EXPRESSION_TEXT;

    public final IElementType REFERENCE_TEXT;

    public final IElementType TYPE_WITH_DISJUNCTIONS_TEXT;

    public final IElementType TYPE_WITH_CONJUNCTIONS_TEXT;

    public final IElementType DUMMY_ELEMENT;

    public JavaElementTypeContainer(IElementType ANNOTATION_PARAMETER_LIST,
                                    IElementType EXTENDS_LIST,
                                    IElementType IMPLEMENTS_LIST,
                                    IElementType FIELD,
                                    IElementType ENUM_CONSTANT,
                                    IElementType METHOD,
                                    IElementType ANNOTATION_METHOD,
                                    IElementType CLASS_INITIALIZER,
                                    IElementType PARAMETER,
                                    IElementType PARAMETER_LIST,
                                    IElementType EXTENDS_BOUND_LIST,
                                    IElementType THROWS_LIST,
                                    IElementType LAMBDA_EXPRESSION,
                                    IElementType METHOD_REF_EXPRESSION,
                                    IElementType MODULE,
                                    IElementType REQUIRES_STATEMENT,
                                    IElementType EXPORTS_STATEMENT,
                                    IElementType OPENS_STATEMENT,
                                    IElementType USES_STATEMENT,
                                    IElementType PROVIDES_STATEMENT,
                                    IElementType PROVIDES_WITH_LIST,
                                    IElementType RECORD_COMPONENT,
                                    IElementType RECORD_HEADER,
                                    IElementType PERMITS_LIST,
                                    IElementType CLASS,
                                    IElementType IMPLICIT_CLASS,
                                    IElementType ANONYMOUS_CLASS,
                                    IElementType ENUM_CONSTANT_INITIALIZER,
                                    IElementType TYPE_PARAMETER_LIST,
                                    IElementType TYPE_PARAMETER,
                                    IElementType IMPORT_LIST,
                                    IElementType IMPORT_STATEMENT,
                                    IElementType IMPORT_STATIC_STATEMENT,
                                    IElementType MODIFIER_LIST,
                                    IElementType ANNOTATION,
                                    IElementType NAME_VALUE_PAIR,
                                    IElementType LITERAL_EXPRESSION,
                                    IElementType IMPORT_STATIC_REFERENCE,
                                    IElementType TYPE,
                                    IElementType DIAMOND_TYPE,
                                    IElementType REFERENCE_PARAMETER_LIST,
                                    IElementType JAVA_CODE_REFERENCE,
                                    IElementType PACKAGE_STATEMENT,
                                    IElementType LOCAL_VARIABLE,
                                    IElementType REFERENCE_EXPRESSION,
                                    IElementType THIS_EXPRESSION,
                                    IElementType SUPER_EXPRESSION,
                                    IElementType PARENTH_EXPRESSION,
                                    IElementType METHOD_CALL_EXPRESSION,
                                    IElementType TYPE_CAST_EXPRESSION,
                                    IElementType PREFIX_EXPRESSION,
                                    IElementType POSTFIX_EXPRESSION,
                                    IElementType BINARY_EXPRESSION,
                                    IElementType POLYADIC_EXPRESSION,
                                    IElementType CONDITIONAL_EXPRESSION,
                                    IElementType ASSIGNMENT_EXPRESSION,
                                    IElementType NEW_EXPRESSION,
                                    IElementType ARRAY_ACCESS_EXPRESSION,
                                    IElementType ARRAY_INITIALIZER_EXPRESSION,
                                    IElementType INSTANCE_OF_EXPRESSION,
                                    IElementType CLASS_OBJECT_ACCESS_EXPRESSION,
                                    IElementType EMPTY_EXPRESSION,
                                    IElementType TEMPLATE_EXPRESSION,
                                    IElementType TEMPLATE,
                                    IElementType EXPRESSION_LIST,
                                    IElementType EMPTY_STATEMENT,
                                    IElementType BLOCK_STATEMENT,
                                    IElementType EXPRESSION_STATEMENT,
                                    IElementType EXPRESSION_LIST_STATEMENT,
                                    IElementType DECLARATION_STATEMENT,
                                    IElementType IF_STATEMENT,
                                    IElementType WHILE_STATEMENT,
                                    IElementType FOR_STATEMENT,
                                    IElementType FOREACH_STATEMENT,
                                    IElementType FOREACH_PATTERN_STATEMENT,
                                    IElementType DO_WHILE_STATEMENT,
                                    IElementType SWITCH_STATEMENT,
                                    IElementType SWITCH_EXPRESSION,
                                    IElementType SWITCH_LABEL_STATEMENT,
                                    IElementType SWITCH_LABELED_RULE,
                                    IElementType BREAK_STATEMENT,
                                    IElementType YIELD_STATEMENT,
                                    IElementType CONTINUE_STATEMENT,
                                    IElementType RETURN_STATEMENT,
                                    IElementType THROW_STATEMENT,
                                    IElementType SYNCHRONIZED_STATEMENT,
                                    IElementType TRY_STATEMENT,
                                    IElementType RESOURCE_LIST,
                                    IElementType RESOURCE_VARIABLE,
                                    IElementType RESOURCE_EXPRESSION,
                                    IElementType CATCH_SECTION,
                                    IElementType LABELED_STATEMENT,
                                    IElementType ASSERT_STATEMENT,
                                    IElementType ANNOTATION_ARRAY_INITIALIZER,
                                    IElementType RECEIVER_PARAMETER,
                                    IElementType MODULE_REFERENCE,
                                    IElementType TYPE_TEST_PATTERN,
                                    IElementType UNNAMED_PATTERN,
                                    IElementType PATTERN_VARIABLE,
                                    IElementType DECONSTRUCTION_PATTERN,
                                    IElementType DECONSTRUCTION_LIST,
                                    IElementType DECONSTRUCTION_PATTERN_VARIABLE,
                                    IElementType PARENTHESIZED_PATTERN,
                                    IElementType DEFAULT_CASE_LABEL_ELEMENT,
                                    IElementType CASE_LABEL_ELEMENT_LIST,
                                    ILazyParseableElementType CODE_BLOCK,
                                    IElementType MEMBERS,
                                    IElementType STATEMENTS,
                                    IElementType EXPRESSION_TEXT,
                                    IElementType REFERENCE_TEXT,
                                    IElementType TYPE_WITH_DISJUNCTIONS_TEXT,
                                    IElementType TYPE_WITH_CONJUNCTIONS_TEXT,
                                    IElementType DUMMY_ELEMENT) {
      this.ANNOTATION_PARAMETER_LIST = ANNOTATION_PARAMETER_LIST;
      this.EXTENDS_LIST = EXTENDS_LIST;
      this.IMPLEMENTS_LIST = IMPLEMENTS_LIST;
      this.FIELD = FIELD;
      this.ENUM_CONSTANT = ENUM_CONSTANT;
      this.METHOD = METHOD;
      this.ANNOTATION_METHOD = ANNOTATION_METHOD;
      this.CLASS_INITIALIZER = CLASS_INITIALIZER;
      this.PARAMETER = PARAMETER;
      this.PARAMETER_LIST = PARAMETER_LIST;
      this.EXTENDS_BOUND_LIST = EXTENDS_BOUND_LIST;
      this.THROWS_LIST = THROWS_LIST;
      this.LAMBDA_EXPRESSION = LAMBDA_EXPRESSION;
      this.METHOD_REF_EXPRESSION = METHOD_REF_EXPRESSION;
      this.MODULE = MODULE;
      this.REQUIRES_STATEMENT = REQUIRES_STATEMENT;
      this.EXPORTS_STATEMENT = EXPORTS_STATEMENT;
      this.OPENS_STATEMENT = OPENS_STATEMENT;
      this.USES_STATEMENT = USES_STATEMENT;
      this.PROVIDES_STATEMENT = PROVIDES_STATEMENT;
      this.PROVIDES_WITH_LIST = PROVIDES_WITH_LIST;
      this.RECORD_COMPONENT = RECORD_COMPONENT;
      this.RECORD_HEADER = RECORD_HEADER;
      this.PERMITS_LIST = PERMITS_LIST;
      this.CLASS = CLASS;
      this.IMPLICIT_CLASS = IMPLICIT_CLASS;
      this.ANONYMOUS_CLASS = ANONYMOUS_CLASS;
      this.ENUM_CONSTANT_INITIALIZER = ENUM_CONSTANT_INITIALIZER;
      this.TYPE_PARAMETER_LIST = TYPE_PARAMETER_LIST;
      this.TYPE_PARAMETER = TYPE_PARAMETER;
      this.IMPORT_LIST = IMPORT_LIST;
      this.IMPORT_STATEMENT = IMPORT_STATEMENT;
      this.IMPORT_STATIC_STATEMENT = IMPORT_STATIC_STATEMENT;
      this.MODIFIER_LIST = MODIFIER_LIST;
      this.ANNOTATION = ANNOTATION;
      this.NAME_VALUE_PAIR = NAME_VALUE_PAIR;
      this.LITERAL_EXPRESSION = LITERAL_EXPRESSION;
      this.IMPORT_STATIC_REFERENCE = IMPORT_STATIC_REFERENCE;
      this.TYPE = TYPE;
      this.DIAMOND_TYPE = DIAMOND_TYPE;
      this.REFERENCE_PARAMETER_LIST = REFERENCE_PARAMETER_LIST;
      this.JAVA_CODE_REFERENCE = JAVA_CODE_REFERENCE;
      this.PACKAGE_STATEMENT = PACKAGE_STATEMENT;
      this.LOCAL_VARIABLE = LOCAL_VARIABLE;
      this.REFERENCE_EXPRESSION = REFERENCE_EXPRESSION;
      this.THIS_EXPRESSION = THIS_EXPRESSION;
      this.SUPER_EXPRESSION = SUPER_EXPRESSION;
      this.PARENTH_EXPRESSION = PARENTH_EXPRESSION;
      this.METHOD_CALL_EXPRESSION = METHOD_CALL_EXPRESSION;
      this.TYPE_CAST_EXPRESSION = TYPE_CAST_EXPRESSION;
      this.PREFIX_EXPRESSION = PREFIX_EXPRESSION;
      this.POSTFIX_EXPRESSION = POSTFIX_EXPRESSION;
      this.BINARY_EXPRESSION = BINARY_EXPRESSION;
      this.POLYADIC_EXPRESSION = POLYADIC_EXPRESSION;
      this.CONDITIONAL_EXPRESSION = CONDITIONAL_EXPRESSION;
      this.ASSIGNMENT_EXPRESSION = ASSIGNMENT_EXPRESSION;
      this.NEW_EXPRESSION = NEW_EXPRESSION;
      this.ARRAY_ACCESS_EXPRESSION = ARRAY_ACCESS_EXPRESSION;
      this.ARRAY_INITIALIZER_EXPRESSION = ARRAY_INITIALIZER_EXPRESSION;
      this.INSTANCE_OF_EXPRESSION = INSTANCE_OF_EXPRESSION;
      this.CLASS_OBJECT_ACCESS_EXPRESSION = CLASS_OBJECT_ACCESS_EXPRESSION;
      this.EMPTY_EXPRESSION = EMPTY_EXPRESSION;
      this.TEMPLATE_EXPRESSION = TEMPLATE_EXPRESSION;
      this.TEMPLATE = TEMPLATE;
      this.EXPRESSION_LIST = EXPRESSION_LIST;
      this.EMPTY_STATEMENT = EMPTY_STATEMENT;
      this.BLOCK_STATEMENT = BLOCK_STATEMENT;
      this.EXPRESSION_STATEMENT = EXPRESSION_STATEMENT;
      this.EXPRESSION_LIST_STATEMENT = EXPRESSION_LIST_STATEMENT;
      this.DECLARATION_STATEMENT = DECLARATION_STATEMENT;
      this.IF_STATEMENT = IF_STATEMENT;
      this.WHILE_STATEMENT = WHILE_STATEMENT;
      this.FOR_STATEMENT = FOR_STATEMENT;
      this.FOREACH_STATEMENT = FOREACH_STATEMENT;
      this.FOREACH_PATTERN_STATEMENT = FOREACH_PATTERN_STATEMENT;
      this.DO_WHILE_STATEMENT = DO_WHILE_STATEMENT;
      this.SWITCH_STATEMENT = SWITCH_STATEMENT;
      this.SWITCH_EXPRESSION = SWITCH_EXPRESSION;
      this.SWITCH_LABEL_STATEMENT = SWITCH_LABEL_STATEMENT;
      this.SWITCH_LABELED_RULE = SWITCH_LABELED_RULE;
      this.BREAK_STATEMENT = BREAK_STATEMENT;
      this.YIELD_STATEMENT = YIELD_STATEMENT;
      this.CONTINUE_STATEMENT = CONTINUE_STATEMENT;
      this.RETURN_STATEMENT = RETURN_STATEMENT;
      this.THROW_STATEMENT = THROW_STATEMENT;
      this.SYNCHRONIZED_STATEMENT = SYNCHRONIZED_STATEMENT;
      this.TRY_STATEMENT = TRY_STATEMENT;
      this.RESOURCE_LIST = RESOURCE_LIST;
      this.RESOURCE_VARIABLE = RESOURCE_VARIABLE;
      this.RESOURCE_EXPRESSION = RESOURCE_EXPRESSION;
      this.CATCH_SECTION = CATCH_SECTION;
      this.LABELED_STATEMENT = LABELED_STATEMENT;
      this.ASSERT_STATEMENT = ASSERT_STATEMENT;
      this.ANNOTATION_ARRAY_INITIALIZER = ANNOTATION_ARRAY_INITIALIZER;
      this.RECEIVER_PARAMETER = RECEIVER_PARAMETER;
      this.MODULE_REFERENCE = MODULE_REFERENCE;
      this.TYPE_TEST_PATTERN = TYPE_TEST_PATTERN;
      this.UNNAMED_PATTERN = UNNAMED_PATTERN;
      this.PATTERN_VARIABLE = PATTERN_VARIABLE;
      this.DECONSTRUCTION_PATTERN = DECONSTRUCTION_PATTERN;
      this.DECONSTRUCTION_LIST = DECONSTRUCTION_LIST;
      this.DECONSTRUCTION_PATTERN_VARIABLE = DECONSTRUCTION_PATTERN_VARIABLE;
      this.PARENTHESIZED_PATTERN = PARENTHESIZED_PATTERN;
      this.DEFAULT_CASE_LABEL_ELEMENT = DEFAULT_CASE_LABEL_ELEMENT;
      this.CASE_LABEL_ELEMENT_LIST = CASE_LABEL_ELEMENT_LIST;
      this.CODE_BLOCK = CODE_BLOCK;
      this.MEMBERS = MEMBERS;
      this.STATEMENTS = STATEMENTS;
      this.EXPRESSION_TEXT = EXPRESSION_TEXT;
      this.REFERENCE_TEXT = REFERENCE_TEXT;
      this.TYPE_WITH_DISJUNCTIONS_TEXT = TYPE_WITH_DISJUNCTIONS_TEXT;
      this.TYPE_WITH_CONJUNCTIONS_TEXT = TYPE_WITH_CONJUNCTIONS_TEXT;
      this.DUMMY_ELEMENT = DUMMY_ELEMENT;
    }
  }

  public abstract JavaElementTypeContainer getContainer();
}
