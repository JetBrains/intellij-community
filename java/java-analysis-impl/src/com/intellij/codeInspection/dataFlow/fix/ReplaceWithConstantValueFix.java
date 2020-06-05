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

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.refactoring.extractMethod.ExtractMethodUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class ReplaceWithConstantValueFix implements LocalQuickFix {
  private final String myPresentableName;
  private final String myReplacementText;

  public ReplaceWithConstantValueFix(String presentableName, String replacementText) {
    myPresentableName = presentableName;
    myReplacementText = replacementText;
  }

  @NotNull
  @Override
  public String getName() {
    return CommonQuickFixBundle.message("fix.replace.with.x", myPresentableName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaAnalysisBundle.message("replace.with.constant.value");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement problemElement = descriptor.getPsiElement();
    if (problemElement == null) return;

    PsiMethodCallExpression call = problemElement.getParent() instanceof PsiExpressionList &&
                                   problemElement.getParent().getParent() instanceof PsiMethodCallExpression ?
                                   (PsiMethodCallExpression)problemElement.getParent().getParent() :
                                   null;
    PsiMethod targetMethod = call == null ? null : call.resolveMethod();

    new CommentTracker().replaceAndRestoreComments(problemElement, myReplacementText);

    if (targetMethod != null) {
      ExtractMethodUtil.addCastsToEnsureResolveTarget(targetMethod, call);
    }
  }
}
