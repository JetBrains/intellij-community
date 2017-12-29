/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.scope.processor.VariablesNotProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class ReuseVariableDeclarationFix implements IntentionAction {
  private final PsiLocalVariable myVariable;

  public ReuseVariableDeclarationFix(@NotNull PsiLocalVariable variable) {
    this.myVariable = variable;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("reuse.variable.declaration.family");
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("reuse.variable.declaration.text", myVariable.getName());
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (!myVariable.isValid()) {
      return false;
    }
    final PsiVariable previousVariable = findPreviousVariable(myVariable);
    return previousVariable != null &&
           Comparing.equal(previousVariable.getType(), myVariable.getType()) &&
           myVariable.getManager().isInProject(myVariable);
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myVariable;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiVariable refVariable = findPreviousVariable(myVariable);
    if (refVariable == null) return;

    final PsiExpression initializer = myVariable.getInitializer();
    if (initializer == null) {
      myVariable.delete();
      return;
    }

    PsiUtil.setModifierProperty(refVariable, PsiModifier.FINAL, false);
    final PsiElementFactory factory = JavaPsiFacade.getInstance(myVariable.getProject()).getElementFactory();
    final PsiElement statement = factory.createStatementFromText(myVariable.getName() + " = " + initializer.getText() + ";", null);
    myVariable.getParent().replace(statement);
  }

  @Nullable
  static PsiVariable findPreviousVariable(PsiLocalVariable variable) {
    PsiElement scope = variable.getParent();
    while (scope != null) {
      if (scope instanceof PsiFile || scope instanceof PsiMethod || scope instanceof PsiClassInitializer) break;
      scope = scope.getParent();
    }
    if (scope == null) return null;

    PsiIdentifier nameIdentifier = variable.getNameIdentifier();
    if (nameIdentifier == null) {
      return null;
    }

    final VariablesNotProcessor processor = new VariablesNotProcessor(variable, false);
    PsiScopesUtil.treeWalkUp(processor, nameIdentifier, scope);
    return processor.size() > 0 ? processor.getResult(0) : null;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
