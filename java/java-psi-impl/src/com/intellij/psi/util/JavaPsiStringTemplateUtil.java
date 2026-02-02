// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFragment;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING_TEMPLATE;

public final class JavaPsiStringTemplateUtil {
  /**
   * @param processor template processor expression to check
   * @return true if the supplied processor is the standard StringTemplate.STR processor; false otherwise
   */
  @Contract("null -> false")
  public static boolean isStrTemplate(@Nullable PsiExpression processor) {
    return isStrTemplate(processor, false);
  }

  /**
   * @param processor       template processor expression to check
   * @param allowUnresolved consider unresolved STR expression as valid when true
   * @return true if the supplied processor is the standard StringTemplate.STR processor; false otherwise
   */
  @Contract("null, _ -> false")
  public static boolean isStrTemplate(@Nullable PsiExpression processor, boolean allowUnresolved) {
    processor = PsiUtil.skipParenthesizedExprDown(processor);
    if (!(processor instanceof PsiReferenceExpression)) {
      return false;
    }
    PsiReferenceExpression referenceExpression = (PsiReferenceExpression)processor;
    if (!"STR".equals(referenceExpression.getReferenceName())) {
      return false;
    }
    PsiElement target = referenceExpression.resolve();
    if (allowUnresolved && target == null && referenceExpression.getQualifierExpression() == null) {
      return true;
    }
    if (!(target instanceof PsiField)) {
      return false;
    }
    PsiField field = (PsiField)target;
    PsiClass containingClass = field.getContainingClass();
    return containingClass != null && JAVA_LANG_STRING_TEMPLATE.equals(containingClass.getQualifiedName());
  }

  public static TextRange getContentRange(PsiFragment fragment) {
    final IElementType tokenType = fragment.getTokenType();

    if (tokenType == JavaTokenType.STRING_TEMPLATE_BEGIN || tokenType == JavaTokenType.STRING_TEMPLATE_MID) {
      final TextRange fragmentRange = fragment.getTextRange();
      return new TextRange(fragmentRange.getStartOffset() + 1, fragmentRange.getEndOffset() - 2);
    }
    else if (tokenType == JavaTokenType.STRING_TEMPLATE_END) {
      final TextRange fragmentRange = fragment.getTextRange();
      return new TextRange(fragmentRange.getStartOffset() + 1, fragmentRange.getEndOffset() - 1);
    }
    else if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN) {
      final TextRange fragmentRange = fragment.getTextRange();
      final String text = fragment.getText();
      int index = 3;
      while (PsiLiteralUtil.isTextBlockWhiteSpace(text.charAt(index))) {
        index++;
      }
      index++;
      return new TextRange(fragmentRange.getStartOffset() + index, fragmentRange.getEndOffset() - 2);
    }
    else if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_MID) {
      final TextRange fragmentRange = fragment.getTextRange();
      return new TextRange(fragmentRange.getStartOffset() + 1, fragmentRange.getEndOffset() - 2);
    }
    else if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_END) {
      final TextRange fragmentRange = fragment.getTextRange();
      return new TextRange(fragmentRange.getStartOffset() + 1, fragmentRange.getEndOffset() - 3);
    }
    else {
      throw new AssertionError();
    }
  }
}
