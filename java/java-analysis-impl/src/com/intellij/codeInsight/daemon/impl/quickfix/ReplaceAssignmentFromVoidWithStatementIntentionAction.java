/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceAssignmentFromVoidWithStatementIntentionAction implements IntentionAction {
  private final PsiElement myParent;
  private final PsiExpression myLExpr;

  public ReplaceAssignmentFromVoidWithStatementIntentionAction(@NotNull PsiElement parent, @NotNull PsiExpression lExpr) {
    myParent = parent;
    myLExpr = lExpr;
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaAnalysisBundle.message("remove.left.side.of.assignment");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myParent.isValid() && myLExpr.isValid();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myParent.replace(myLExpr);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new ReplaceAssignmentFromVoidWithStatementIntentionAction(PsiTreeUtil.findSameElementInCopy(myParent, target), 
                                                                     PsiTreeUtil.findSameElementInCopy(myLExpr, target));
  }
}
