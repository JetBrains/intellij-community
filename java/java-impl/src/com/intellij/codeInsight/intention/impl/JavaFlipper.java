// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.openapi.editor.actions.FlipCommaIntention;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class JavaFlipper implements FlipCommaIntention.Flipper {
  @Override
  public boolean flip(@NotNull PsiElement left, @NotNull PsiElement right) {
    if (left instanceof PsiVariable leftVariable && right instanceof PsiVariable rightVariable) {
      if (left instanceof PsiParameter || right instanceof PsiParameter) return false;
      if (left instanceof PsiRecordComponent || right instanceof PsiRecordComponent) return false;
      if (left instanceof PsiEnumConstant && ErrorUtil.containsDeepError(left) ||
          right instanceof PsiEnumConstant && ErrorUtil.containsDeepError(right)) {
        return false;
      }
      // flips multiple variables in a single declaration
      final PsiIdentifier leftIdentifier = leftVariable.getNameIdentifier();
      assert leftIdentifier != null;
      final PsiIdentifier rightIdentifier = rightVariable.getNameIdentifier();
      assert rightIdentifier != null;
      PsiElement leftLast = leftVariable.getLastChild();
      leftLast = PsiUtil.isJavaToken(leftLast, JavaTokenType.SEMICOLON) ? leftLast.getPrevSibling() : leftLast;
      PsiElement rightLast = rightVariable.getLastChild();
      rightLast = PsiUtil.isJavaToken(rightLast, JavaTokenType.SEMICOLON) ? rightLast.getPrevSibling() : rightLast;
      left.addRangeBefore(rightIdentifier, rightLast, leftIdentifier);
      right.addRangeBefore(leftIdentifier, leftLast, rightIdentifier);
      left.deleteChildRange(leftIdentifier, leftLast);
      right.deleteChildRange(rightIdentifier, rightLast);
      return true;
    }
    return false;
  }
}
