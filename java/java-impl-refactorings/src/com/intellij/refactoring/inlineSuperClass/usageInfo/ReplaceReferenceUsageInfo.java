/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;

public class ReplaceReferenceUsageInfo extends FixableUsageInfo {
  private final PsiClass myTargetClass;
  private final @Nls String myConflict;

  public ReplaceReferenceUsageInfo(PsiElement referenceExpression, PsiClass[] targetClasses) {
    super(referenceExpression);
    myTargetClass = targetClasses[0];
    myConflict = targetClasses.length > 1 ? JavaRefactoringBundle.message("inline.super.expr.can.be.replaced",
                                                                          referenceExpression.getText(),
                                                                          StringUtil.join(targetClasses,
                                                                                          psiClass -> psiClass.getQualifiedName(), ", "))
                                          : null;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    final PsiElement referenceExpression = getElement();
    if (referenceExpression != null) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getProject());
      referenceExpression.replace(referenceExpression instanceof PsiReferenceExpression ? elementFactory.createReferenceExpression(myTargetClass) : elementFactory.createClassReferenceElement(myTargetClass));
    }
  }

  @Override
  public String getConflictMessage() {
    return myConflict;
  }
}