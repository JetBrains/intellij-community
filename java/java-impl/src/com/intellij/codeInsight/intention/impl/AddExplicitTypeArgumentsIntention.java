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

import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeArgumentsFix;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class AddExplicitTypeArgumentsIntention extends BaseElementAtCaretIntentionAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.add.explicit.type.arguments.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiIdentifier identifier = ObjectUtils.tryCast(element, PsiIdentifier.class);
    if (identifier == null) return false;
    PsiReferenceExpression methodExpression = ObjectUtils.tryCast(identifier.getParent(), PsiReferenceExpression.class);
    if (methodExpression == null) return false;
    PsiElement parent = methodExpression.getParent();
    if (parent instanceof PsiMethodCallExpression && ((PsiMethodCallExpression)parent).getTypeArguments().length == 0) {
      PsiMethodCallExpression callExpression = (PsiMethodCallExpression)parent;
      JavaResolveResult result = callExpression.resolveMethodGenerics();
      if (result instanceof MethodCandidateInfo candidateInfo && candidateInfo.isApplicable()) {
        PsiMethod method = candidateInfo.getElement();
        setText(getFamilyName());
        return !method.isConstructor() && method.hasTypeParameters() && AddTypeArgumentsFix.addTypeArguments(callExpression, null) != null;
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    assert callExpression != null;
    PsiExpression withArgs = AddTypeArgumentsFix.addTypeArguments(callExpression, null);
    if (withArgs != null) {
      CodeStyleManager.getInstance(project).reformat(callExpression.replace(withArgs));
    }
  }
}