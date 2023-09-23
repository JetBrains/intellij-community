// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.BasicElementTypes;
import com.intellij.psi.tree.TokenSet;

public interface ElementType extends JavaTokenType, JavaDocTokenType, JavaElementType, JavaDocElementType {
  TokenSet JAVA_PLAIN_COMMENT_BIT_SET = BasicElementTypes.BASIC_JAVA_PLAIN_COMMENT_BIT_SET;
  TokenSet JAVA_COMMENT_BIT_SET = BasicElementTypes.BASIC_JAVA_COMMENT_BIT_SET.toTokenSet();
  TokenSet JAVA_COMMENT_OR_WHITESPACE_BIT_SET = BasicElementTypes.BASIC_JAVA_COMMENT_OR_WHITESPACE_BIT_SET.toTokenSet();

  TokenSet KEYWORD_BIT_SET = BasicElementTypes.BASIC_KEYWORD_BIT_SET;

  TokenSet LITERAL_BIT_SET = BasicElementTypes.BASIC_LITERAL_BIT_SET;

  TokenSet OPERATION_BIT_SET = BasicElementTypes.BASIC_OPERATION_BIT_SET;

  TokenSet MODIFIER_BIT_SET = BasicElementTypes.BASIC_MODIFIER_BIT_SET.toTokenSet();

  TokenSet PRIMITIVE_TYPE_BIT_SET = BasicElementTypes.BASIC_PRIMITIVE_TYPE_BIT_SET.toTokenSet();

  TokenSet EXPRESSION_BIT_SET = BasicElementTypes.BASIC_EXPRESSION_BIT_SET.toTokenSet();

  TokenSet ANNOTATION_MEMBER_VALUE_BIT_SET = BasicElementTypes.BASIC_ANNOTATION_MEMBER_VALUE_BIT_SET.toTokenSet();
  TokenSet ARRAY_DIMENSION_BIT_SET = BasicElementTypes.BASIC_ARRAY_DIMENSION_BIT_SET.toTokenSet();

  TokenSet JAVA_STATEMENT_BIT_SET = BasicElementTypes.BASIC_JAVA_STATEMENT_BIT_SET.toTokenSet();

  TokenSet JAVA_PATTERN_BIT_SET = BasicElementTypes.BASIC_JAVA_PATTERN_BIT_SET.toTokenSet();

  TokenSet JAVA_CASE_LABEL_ELEMENT_BIT_SET = BasicElementTypes.BASIC_JAVA_CASE_LABEL_ELEMENT_BIT_SET.toTokenSet();

  TokenSet JAVA_MODULE_STATEMENT_BIT_SET = BasicElementTypes.BASIC_JAVA_MODULE_STATEMENT_BIT_SET.toTokenSet();

  TokenSet IMPORT_STATEMENT_BASE_BIT_SET = BasicElementTypes.BASIC_IMPORT_STATEMENT_BASE_BIT_SET.toTokenSet();
  TokenSet CLASS_KEYWORD_BIT_SET = BasicElementTypes.BASIC_CLASS_KEYWORD_BIT_SET.toTokenSet();
  TokenSet MEMBER_BIT_SET = BasicElementTypes.BASIC_MEMBER_BIT_SET.toTokenSet();
  TokenSet FULL_MEMBER_BIT_SET = BasicElementTypes.BASIC_FULL_MEMBER_BIT_SET.toTokenSet();

  TokenSet INTEGER_LITERALS = BasicElementTypes.BASIC_INTEGER_LITERALS;
  TokenSet REAL_LITERALS = BasicElementTypes.BASIC_REAL_LITERALS;
  TokenSet STRING_LITERALS = BasicElementTypes.BASIC_STRING_LITERALS;
  TokenSet TEXT_LITERALS = BasicElementTypes.BASIC_TEXT_LITERALS;

  TokenSet STRING_TEMPLATE_FRAGMENTS = BasicElementTypes.BASIC_STRING_TEMPLATE_FRAGMENTS;

  TokenSet ALL_LITERALS = BasicElementTypes.BASIC_ALL_LITERALS;
}