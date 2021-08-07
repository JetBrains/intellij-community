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

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RedundantInstanceofFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public RedundantInstanceofFix(@Nullable PsiElement element) {
    super(element);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaAnalysisBundle.message("inspection.data.flow.redundant.instanceof.quickfix");
  }

  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    PsiElement psiElement = startElement;
    CommentTracker ct = new CommentTracker();
    if (psiElement instanceof PsiMethodReferenceExpression) {
      String replacement = CommonClassNames.JAVA_UTIL_OBJECTS + "::nonNull";
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(ct.replaceAndRestoreComments(psiElement, replacement));
      return;
    }
    String nonNullExpression = null;
    if (psiElement instanceof PsiInstanceOfExpression) {
      nonNullExpression = ct.text(((PsiInstanceOfExpression)psiElement).getOperand());
    }
    else if (psiElement instanceof PsiMethodCallExpression) {
      PsiExpression arg = ArrayUtil.getFirstElement(((PsiMethodCallExpression)psiElement).getArgumentList().getExpressions());
      if (arg == null) return;
      nonNullExpression = ct.text(arg);
    }
    if (nonNullExpression == null) return;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(psiElement.getParent());
    String replacement;
    if (parent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)parent)) {
      replacement = nonNullExpression + "==null";
      psiElement = parent;
    } else {
      replacement = nonNullExpression + "!=null";
    }
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(ct.replaceAndRestoreComments(psiElement, replacement));
  }
}
