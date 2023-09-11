// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.BasicElementTypes;
import com.intellij.psi.tree.TokenSet;

public interface ElementType extends JavaTokenType, JavaDocTokenType, JavaElementType, JavaDocElementType {
  TokenSet JAVA_PLAIN_COMMENT_BIT_SET = BasicElementTypes.JAVA_PLAIN_COMMENT_BIT_SET.toTokenSet();
  TokenSet JAVA_COMMENT_BIT_SET = BasicElementTypes.JAVA_COMMENT_BIT_SET.toTokenSet();
  TokenSet JAVA_COMMENT_OR_WHITESPACE_BIT_SET = BasicElementTypes.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.toTokenSet();

  TokenSet KEYWORD_BIT_SET = BasicElementTypes.KEYWORD_BIT_SET.toTokenSet();

  TokenSet LITERAL_BIT_SET = BasicElementTypes.LITERAL_BIT_SET.toTokenSet();

  TokenSet OPERATION_BIT_SET = BasicElementTypes.OPERATION_BIT_SET.toTokenSet();

  TokenSet MODIFIER_BIT_SET = BasicElementTypes.MODIFIER_BIT_SET.toTokenSet();

  TokenSet PRIMITIVE_TYPE_BIT_SET = BasicElementTypes.PRIMITIVE_TYPE_BIT_SET.toTokenSet();

  TokenSet EXPRESSION_BIT_SET = BasicElementTypes.EXPRESSION_BIT_SET.toTokenSet();

  TokenSet ANNOTATION_MEMBER_VALUE_BIT_SET = BasicElementTypes.ANNOTATION_MEMBER_VALUE_BIT_SET.toTokenSet();
  TokenSet ARRAY_DIMENSION_BIT_SET = BasicElementTypes.ARRAY_DIMENSION_BIT_SET.toTokenSet();

  TokenSet JAVA_STATEMENT_BIT_SET = BasicElementTypes.JAVA_STATEMENT_BIT_SET.toTokenSet();

  TokenSet JAVA_PATTERN_BIT_SET = BasicElementTypes.JAVA_PATTERN_BIT_SET.toTokenSet();

  TokenSet JAVA_CASE_LABEL_ELEMENT_BIT_SET = BasicElementTypes.JAVA_CASE_LABEL_ELEMENT_BIT_SET.toTokenSet();

  TokenSet JAVA_MODULE_STATEMENT_BIT_SET = BasicElementTypes.JAVA_MODULE_STATEMENT_BIT_SET.toTokenSet();

  TokenSet IMPORT_STATEMENT_BASE_BIT_SET = BasicElementTypes.IMPORT_STATEMENT_BASE_BIT_SET.toTokenSet();
  TokenSet CLASS_KEYWORD_BIT_SET = BasicElementTypes.CLASS_KEYWORD_BIT_SET.toTokenSet();
  TokenSet MEMBER_BIT_SET = BasicElementTypes.MEMBER_BIT_SET.toTokenSet();
  TokenSet FULL_MEMBER_BIT_SET = BasicElementTypes.FULL_MEMBER_BIT_SET.toTokenSet();

  TokenSet INTEGER_LITERALS = BasicElementTypes.INTEGER_LITERALS.toTokenSet();
  TokenSet REAL_LITERALS = BasicElementTypes.REAL_LITERALS.toTokenSet();
  TokenSet STRING_LITERALS = BasicElementTypes.STRING_LITERALS.toTokenSet();
  TokenSet TEXT_LITERALS = BasicElementTypes.TEXT_LITERALS.toTokenSet();

  TokenSet STRING_TEMPLATE_FRAGMENTS = BasicElementTypes.STRING_TEMPLATE_FRAGMENTS.toTokenSet();

  TokenSet ALL_LITERALS = BasicElementTypes.ALL_LITERALS.toTokenSet();
}