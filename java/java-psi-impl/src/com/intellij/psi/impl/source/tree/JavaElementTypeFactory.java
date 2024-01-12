// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.impl.source.AbstractBasicJavaElementTypeFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public class JavaElementTypeFactory extends AbstractBasicJavaElementTypeFactory {

  public static final JavaElementTypeFactory INSTANCE = new JavaElementTypeFactory();

  @Override
  public JavaElementTypeContainer getContainer() {
    return SingletonHelper.INSTANCE;
  }

  private static class SingletonHelper {
    private static final JavaElementTypeContainer INSTANCE = getJavaElementTypeContainer();
  }

  @NotNull
  private static JavaElementTypeContainer getJavaElementTypeContainer() {
    return new JavaElementTypeContainer(
      JavaElementType.ANNOTATION_PARAMETER_LIST,
      JavaElementType.EXTENDS_LIST,
      JavaElementType.IMPLEMENTS_LIST,
      JavaElementType.FIELD,
      JavaElementType.ENUM_CONSTANT,
      JavaElementType.METHOD,
      JavaElementType.ANNOTATION_METHOD,
      JavaElementType.CLASS_INITIALIZER,
      JavaElementType.PARAMETER,
      JavaElementType.PARAMETER_LIST,
      JavaElementType.EXTENDS_BOUND_LIST,
      JavaElementType.THROWS_LIST,
      JavaElementType.LAMBDA_EXPRESSION,
      JavaElementType.METHOD_REF_EXPRESSION,
      JavaElementType.MODULE,
      JavaElementType.REQUIRES_STATEMENT,
      JavaElementType.EXPORTS_STATEMENT,
      JavaElementType.OPENS_STATEMENT,
      JavaElementType.USES_STATEMENT,
      JavaElementType.PROVIDES_STATEMENT,
      JavaElementType.PROVIDES_WITH_LIST,
      JavaElementType.RECORD_COMPONENT,
      JavaElementType.RECORD_HEADER,
      JavaElementType.PERMITS_LIST,
      JavaElementType.CLASS,
      JavaElementType.IMPLICIT_CLASS,
      JavaElementType.ANONYMOUS_CLASS,
      JavaElementType.ENUM_CONSTANT_INITIALIZER,
      JavaElementType.TYPE_PARAMETER_LIST,
      JavaElementType.TYPE_PARAMETER,
      JavaElementType.IMPORT_LIST,
      JavaElementType.IMPORT_STATEMENT,
      JavaElementType.IMPORT_STATIC_STATEMENT,
      JavaElementType.MODIFIER_LIST,
      JavaElementType.ANNOTATION,
      JavaElementType.NAME_VALUE_PAIR,
      JavaElementType.LITERAL_EXPRESSION,
      JavaElementType.IMPORT_STATIC_REFERENCE,
      JavaElementType.TYPE,
      JavaElementType.DIAMOND_TYPE,
      JavaElementType.REFERENCE_PARAMETER_LIST,
      JavaElementType.JAVA_CODE_REFERENCE,
      JavaElementType.PACKAGE_STATEMENT,
      JavaElementType.LOCAL_VARIABLE,
      JavaElementType.REFERENCE_EXPRESSION,
      JavaElementType.THIS_EXPRESSION,
      JavaElementType.SUPER_EXPRESSION,
      JavaElementType.PARENTH_EXPRESSION,
      JavaElementType.METHOD_CALL_EXPRESSION,
      JavaElementType.TYPE_CAST_EXPRESSION,
      JavaElementType.PREFIX_EXPRESSION,
      JavaElementType.POSTFIX_EXPRESSION,
      JavaElementType.BINARY_EXPRESSION,
      JavaElementType.POLYADIC_EXPRESSION,
      JavaElementType.CONDITIONAL_EXPRESSION,
      JavaElementType.ASSIGNMENT_EXPRESSION,
      JavaElementType.NEW_EXPRESSION,
      JavaElementType.ARRAY_ACCESS_EXPRESSION,
      JavaElementType.ARRAY_INITIALIZER_EXPRESSION,
      JavaElementType.INSTANCE_OF_EXPRESSION,
      JavaElementType.CLASS_OBJECT_ACCESS_EXPRESSION,
      JavaElementType.EMPTY_EXPRESSION,
      JavaElementType.TEMPLATE_EXPRESSION,
      JavaElementType.TEMPLATE,
      JavaElementType.EXPRESSION_LIST,
      JavaElementType.EMPTY_STATEMENT,
      JavaElementType.BLOCK_STATEMENT,
      JavaElementType.EXPRESSION_STATEMENT,
      JavaElementType.EXPRESSION_LIST_STATEMENT,
      JavaElementType.DECLARATION_STATEMENT,
      JavaElementType.IF_STATEMENT,
      JavaElementType.WHILE_STATEMENT,
      JavaElementType.FOR_STATEMENT,
      JavaElementType.FOREACH_STATEMENT,
      JavaElementType.FOREACH_PATTERN_STATEMENT,
      JavaElementType.DO_WHILE_STATEMENT,
      JavaElementType.SWITCH_STATEMENT,
      JavaElementType.SWITCH_EXPRESSION,
      JavaElementType.SWITCH_LABEL_STATEMENT,
      JavaElementType.SWITCH_LABELED_RULE,
      JavaElementType.BREAK_STATEMENT,
      JavaElementType.YIELD_STATEMENT,
      JavaElementType.CONTINUE_STATEMENT,
      JavaElementType.RETURN_STATEMENT,
      JavaElementType.THROW_STATEMENT,
      JavaElementType.SYNCHRONIZED_STATEMENT,
      JavaElementType.TRY_STATEMENT,
      JavaElementType.RESOURCE_LIST,
      JavaElementType.RESOURCE_VARIABLE,
      JavaElementType.RESOURCE_EXPRESSION,
      JavaElementType.CATCH_SECTION,
      JavaElementType.LABELED_STATEMENT,
      JavaElementType.ASSERT_STATEMENT,
      JavaElementType.ANNOTATION_ARRAY_INITIALIZER,
      JavaElementType.RECEIVER_PARAMETER,
      JavaElementType.MODULE_REFERENCE,
      JavaElementType.TYPE_TEST_PATTERN,
      JavaElementType.UNNAMED_PATTERN,
      JavaElementType.PATTERN_VARIABLE,
      JavaElementType.DECONSTRUCTION_PATTERN,
      JavaElementType.DECONSTRUCTION_LIST,
      JavaElementType.DECONSTRUCTION_PATTERN_VARIABLE,
      JavaElementType.PARENTHESIZED_PATTERN,
      JavaElementType.DEFAULT_CASE_LABEL_ELEMENT,
      JavaElementType.CASE_LABEL_ELEMENT_LIST,
      JavaElementType.CODE_BLOCK,
      JavaElementType.MEMBERS,
      JavaElementType.STATEMENTS,
      JavaElementType.EXPRESSION_TEXT,
      JavaElementType.REFERENCE_TEXT,
      JavaElementType.TYPE_WITH_DISJUNCTIONS_TEXT,
      JavaElementType.TYPE_WITH_CONJUNCTIONS_TEXT,
      JavaElementType.FILE_FRAGMENT,
      JavaElementType.DUMMY_ELEMENT
    );
  }
}
