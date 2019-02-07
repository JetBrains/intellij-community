/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.openapi.editor.actions.FlipCommaIntention;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ipp.psiutils.ErrorUtil;

/**
 * @author Bas Leijdekkers
 */
public class JavaFlipper implements FlipCommaIntention.Flipper {

  @Override
  public boolean flip(PsiElement left, PsiElement right) {
    if (left instanceof PsiVariable && right instanceof PsiVariable) {
      if (left instanceof PsiParameter || right instanceof PsiParameter) return false;
      if (left instanceof PsiEnumConstant && ErrorUtil.containsDeepError(left) ||
          right instanceof PsiEnumConstant && ErrorUtil.containsDeepError(right)) {
        return false;
      }
      // flips multiple variables in a single declaration
      final PsiVariable leftVariable = (PsiVariable)left;
      final PsiVariable rightVariable = (PsiVariable)right;
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
