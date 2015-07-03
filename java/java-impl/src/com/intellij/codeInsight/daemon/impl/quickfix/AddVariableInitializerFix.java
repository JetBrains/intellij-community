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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AddVariableInitializerFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddReturnFix");
  private final PsiVariable myVariable;

  public AddVariableInitializerFix(@NotNull PsiVariable variable) {
    myVariable = variable;
  }

  @Override
  @NotNull
  public String getText() {
    return CodeInsightBundle.message("quickfix.add.variable.text", myVariable.getName());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("quickfix.add.variable.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return GeneratedSourcesFilter.isInProjectAndNotGenerated(myVariable) &&
           !myVariable.hasInitializer() &&
           !(myVariable instanceof PsiParameter)
        ;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(myVariable.getContainingFile())) return;

    String initializerText = suggestInitializer();
    PsiElementFactory factory = JavaPsiFacade.getInstance(myVariable.getProject()).getElementFactory();
    PsiExpression initializer = factory.createExpressionFromText(initializerText, myVariable);
    if (myVariable instanceof PsiLocalVariable) {
      ((PsiLocalVariable)myVariable).setInitializer(initializer);
    }
    else if (myVariable instanceof PsiField) {
      ((PsiField)myVariable).setInitializer(initializer);
    }
    else {
      LOG.error("Unknown variable type: "+myVariable);
    }
    PsiVariable var = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(myVariable);
    TextRange range = var.getInitializer().getTextRange();
    int offset = range.getStartOffset();
    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
  }

  private String suggestInitializer() {
    PsiType type = myVariable.getType();
    return PsiTypesUtil.getDefaultValueOfType(type);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
