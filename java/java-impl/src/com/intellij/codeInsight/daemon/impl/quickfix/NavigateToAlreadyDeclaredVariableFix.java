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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiVariable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class NavigateToAlreadyDeclaredVariableFix implements IntentionAction {
  private final PsiLocalVariable myVariable;

  public NavigateToAlreadyDeclaredVariableFix(@NotNull PsiLocalVariable variable) {
    this.myVariable = variable;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("navigate.variable.declaration.family");
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("navigate.variable.declaration.text", myVariable.getName());
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (!myVariable.isValid()) {
      return false;
    }
    final PsiVariable previousVariable = ReuseVariableDeclarationFix.findPreviousVariable(myVariable);
    return previousVariable != null &&
           myVariable.getManager().isInProject(myVariable);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiVariable refVariable = ReuseVariableDeclarationFix.findPreviousVariable(myVariable);
    if (refVariable == null) return;

    refVariable.navigate(true);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
