/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring.move.moveInner;

import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveInnerClassJavaUsagesHandler implements MoveInnerClassUsagesHandler {
  @Override
  public void correctInnerClassUsage(@NotNull UsageInfo usage, @NotNull PsiClass outerClass, @Nullable String parameterNameOuterClass) {
    PsiElement refElement = usage.getElement();
    if (refElement == null) return;

    PsiManager manager = refElement.getManager();

    PsiElement refParent = refElement.getParent();
    if (refParent instanceof PsiNewExpression || refParent instanceof PsiAnonymousClass) {
      PsiNewExpression newExpr = refParent instanceof PsiNewExpression
                                 ? (PsiNewExpression)refParent
                                 : (PsiNewExpression)refParent.getParent();

      PsiExpressionList argList = newExpr.getArgumentList();

      if (argList != null) { // can happen in incomplete code
        PsiExpression qualifier = newExpr.getQualifier();
        if (qualifier != null) {
          if (parameterNameOuterClass != null) {
            argList.addAfter(qualifier, null);
          }
          qualifier.delete();
        }
        else if (parameterNameOuterClass != null) {
          PsiThisExpression thisExpr;
          PsiClass parentClass = RefactoringChangeUtil.getThisClass(newExpr);
          if (outerClass.equals(parentClass)) {
            thisExpr = RefactoringChangeUtil.createThisExpression(manager, null);
          }
          else {
            thisExpr = RefactoringChangeUtil.createThisExpression(manager, outerClass);
          }
          argList.addAfter(thisExpr, null);
        }
      }
    }
  }

  @Override
  public void correctInnerClassUsage(@NotNull UsageInfo usage, @NotNull PsiClass outerClass) {
    correctInnerClassUsage(usage, outerClass, null);
  }
}