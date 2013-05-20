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
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.MethodThrowsFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class DeleteThrowsFix implements LocalQuickFix {
  private final MethodThrowsFix myQuickFix;

  public DeleteThrowsFix(@NotNull PsiMethod method, PsiClassType exceptionClass) {
    myQuickFix = new MethodThrowsFix(method, exceptionClass, false, false);
  }

  @Override
  @NotNull
  public String getName() {
    return myQuickFix.getText();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.throws.list.family");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (method == null) return;
    final PsiFile psiFile = method.getContainingFile();
    if (myQuickFix.isAvailable(project, psiFile, method, method)) {
      myQuickFix.invoke(project, psiFile, method, method);
    }
  }
}
