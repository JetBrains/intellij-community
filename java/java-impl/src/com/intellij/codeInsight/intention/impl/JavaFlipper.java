/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author Bas Leijdekkers
 */
public class JavaFlipper implements FlipCommaIntention.Flipper {

  @Override
  public boolean flip(PsiElement left, PsiElement right) {
    if (left instanceof PsiVariable && right instanceof PsiVariable) {
      final PsiElement first = left.getFirstChild();
      if (!(first instanceof PsiModifierList)) {
        return false;
      }
      final PsiElement child = PsiTreeUtil.skipSiblingsForward(first, PsiWhiteSpace.class);
      if (!(child instanceof PsiTypeElement)) {
        return false;
      }
      final PsiElement last = child.getNextSibling();
      if (!(last instanceof PsiWhiteSpace)) {
        return false;
      }
      final PsiElement anchor = right.getFirstChild();
      if (!(anchor instanceof PsiIdentifier)) {
        return false;
      }
      final PsiElement semiColon = right.getLastChild();
      if (!(semiColon instanceof PsiJavaToken)) {
        return false;
      }
      right.addRangeBefore(first, last, anchor);
      left.deleteChildRange(first, last);
      left.add(semiColon);
      semiColon.delete();
      final PsiElement copy = left.copy();
      left.replace(right);
      right.replace(copy);
      return true;
    }
    return false;
  }
}
