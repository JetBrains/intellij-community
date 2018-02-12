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
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class SurroundWithRequireNonNullFix implements LocalQuickFix {
  private final String myText;
  private final SmartPsiElementPointer<PsiExpression> myQualifierPointer;

  public SurroundWithRequireNonNullFix(@NotNull PsiExpression expressionToSurround) {
    myText = expressionToSurround.getText();
    myQualifierPointer =
      SmartPointerManager.getInstance(expressionToSurround.getProject()).createSmartPsiElementPointer(expressionToSurround);
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return InspectionsBundle.message("inspection.surround.requirenonnull.quickfix", myText);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionsBundle.message("inspection.surround.requirenonnull.quickfix", "");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiExpression qualifier = myQualifierPointer.getElement();
    if (qualifier == null) return;
    PsiExpression replacement = JavaPsiFacade.getElementFactory(project)
      .createExpressionFromText("java.util.Objects.requireNonNull(" + qualifier.getText() + ")", qualifier);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(qualifier.replace(replacement));
  }
}
