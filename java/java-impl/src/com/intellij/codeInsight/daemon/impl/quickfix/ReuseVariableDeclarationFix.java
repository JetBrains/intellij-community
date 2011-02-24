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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.scope.processor.VariablesNotProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 * Date: Nov 20, 2002
 */
public class ReuseVariableDeclarationFix implements IntentionAction {
  private final PsiVariable myVariable;
  private final PsiIdentifier myIdentifier;

  public ReuseVariableDeclarationFix(final PsiVariable variable, final PsiIdentifier identifier) {
    this.myVariable = variable;
    this.myIdentifier = identifier;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("reuse.variable.declaration.family");
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("reuse.variable.declaration.text", myVariable.getName());
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    final PsiVariable previousVariable = findPreviousVariable();
    return myVariable != null &&
           myVariable.isValid() &&
           myVariable instanceof PsiLocalVariable &&
           !(myVariable.getParent() instanceof PsiResourceVariable && myVariable.getInitializer() == null) &&
           previousVariable != null &&
           Comparing.equal(previousVariable.getType(), myVariable.getType()) &&
           myIdentifier != null &&
           myIdentifier.isValid() &&
           myVariable.getManager().isInProject(myVariable);
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiVariable refVariable = findPreviousVariable();
    if (refVariable == null) return;

    if (!CodeInsightUtil.preparePsiElementsForWrite(myVariable, refVariable)) return;

    final PsiExpression initializer = myVariable.getInitializer();
    if (initializer == null) {
      myVariable.delete();
      return;
    }

    PsiUtil.setModifierProperty(refVariable, PsiModifier.FINAL, false);

    final PsiElementFactory factory = JavaPsiFacade.getInstance(myVariable.getProject()).getElementFactory();
    final PsiElement replacement;
    final PsiElement parent = myVariable.getParent();
    if (parent instanceof PsiResourceVariable) {
      replacement = factory.createResourceFromText(myVariable.getName() + " = " + initializer.getText(), null);
    }
    else {
      replacement = factory.createStatementFromText(myVariable.getName() + " = " + initializer.getText() + ";", null);
    }
    parent.replace(replacement);
  }

  @Nullable
  private PsiVariable findPreviousVariable() {
    PsiElement scope = myVariable.getParent();
    while (scope != null) {
      if (scope instanceof PsiFile || scope instanceof PsiMethod || scope instanceof PsiClassInitializer) break;
      scope = scope.getParent();
    }
    if (scope == null) return null;

    final VariablesNotProcessor proc = new VariablesNotProcessor(myVariable, false);
    PsiScopesUtil.treeWalkUp(proc, myIdentifier, scope);
    return proc.size() > 0 ? proc.getResult(0) : null;
  }

  public boolean startInWriteAction() {
    return true;
  }
}
