// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.java.syntax.element.SyntaxElementTypes;
import com.intellij.platform.syntax.psi.ElementTypeConverterKt;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.TokenSet;

import static com.intellij.lang.java.syntax.JavaElementTypeConverterExtensionKt.javaPsiElementTypeConverter;

/**
 * @see SyntaxElementTypes
 */
@SuppressWarnings("unused") //because of backward compatibility, some of these sets are used in plugins
public interface ElementType extends JavaTokenType, JavaDocTokenType, JavaElementType, JavaDocElementType {

  TokenSet JAVA_PLAIN_COMMENT_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getJAVA_PLAIN_COMMENT_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet JAVA_COMMENT_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getJAVA_COMMENT_BIT_SET(), javaPsiElementTypeConverter);

  //is not changed, because it includes white_space
  TokenSet JAVA_COMMENT_OR_WHITESPACE_BIT_SET = TokenSet.orSet(TokenSet.WHITE_SPACE, JAVA_COMMENT_BIT_SET);

  TokenSet KEYWORD_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getKEYWORD_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet LITERAL_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getLITERAL_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet OPERATION_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getOPERATION_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet MODIFIER_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getMODIFIER_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet PRIMITIVE_TYPE_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getPRIMITIVE_TYPE_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet EXPRESSION_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getEXPRESSION_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet ANNOTATION_MEMBER_VALUE_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getANNOTATION_MEMBER_VALUE_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet ARRAY_DIMENSION_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getARRAY_DIMENSION_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet JAVA_STATEMENT_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getJAVA_STATEMENT_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet JAVA_PATTERN_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getJAVA_PATTERN_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet JAVA_CASE_LABEL_ELEMENT_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getJAVA_CASE_LABEL_ELEMENT_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet JAVA_MODULE_STATEMENT_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getJAVA_MODULE_STATEMENT_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet IMPORT_STATEMENT_BASE_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getIMPORT_STATEMENT_BASE_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet CLASS_KEYWORD_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getCLASS_KEYWORD_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet MEMBER_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getMEMBER_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet FULL_MEMBER_BIT_SET =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getFULL_MEMBER_BIT_SET(), javaPsiElementTypeConverter);

  TokenSet INTEGER_LITERALS =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getINTEGER_LITERALS(), javaPsiElementTypeConverter);

  TokenSet REAL_LITERALS =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getREAL_LITERALS(), javaPsiElementTypeConverter);

  TokenSet STRING_LITERALS =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getSTRING_LITERALS(), javaPsiElementTypeConverter);

  TokenSet TEXT_LITERALS =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getTEXT_LITERALS(), javaPsiElementTypeConverter);

  TokenSet STRING_TEMPLATE_FRAGMENTS =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getSTRING_TEMPLATE_FRAGMENTS(), javaPsiElementTypeConverter);

  TokenSet ALL_LITERALS =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getALL_LITERALS(), javaPsiElementTypeConverter);

  TokenSet SHIFT_OPS =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getSHIFT_OPS(), javaPsiElementTypeConverter);

  TokenSet ADDITIVE_OPS =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getADDITIVE_OPS(), javaPsiElementTypeConverter);

  TokenSet MULTIPLICATIVE_OPS =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getMULTIPLICATIVE_OPS(), javaPsiElementTypeConverter);

  TokenSet ASSIGNMENT_OPS =
    ElementTypeConverterKt.asTokenSet(SyntaxElementTypes.INSTANCE.getASSIGNMENT_OPS(), javaPsiElementTypeConverter);
}