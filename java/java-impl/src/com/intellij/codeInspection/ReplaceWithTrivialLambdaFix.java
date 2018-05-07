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
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ReplaceWithTrivialLambdaFix implements LocalQuickFix {
  private final String myValue;

  public ReplaceWithTrivialLambdaFix(Object value) {
    myValue = String.valueOf(value);
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return InspectionsBundle.message("inspection.replace.with.trivial.lambda.fix.name", myValue);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionsBundle.message("inspection.replace.with.trivial.lambda.fix.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiMethodReferenceExpression methodRef = ObjectUtils.tryCast(descriptor.getStartElement(), PsiMethodReferenceExpression.class);
    if (methodRef == null) return;
    PsiLambdaExpression lambdaExpression = LambdaRefactoringUtil.convertMethodReferenceToLambda(methodRef, true, true);
    if (lambdaExpression == null) return;
    PsiElement body = lambdaExpression.getBody();
    if (body == null) return;
    body.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(myValue, lambdaExpression));
  }
}
