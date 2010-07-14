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

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AddReturnFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddReturnFix");
  private final PsiMethod myMethod;

  public AddReturnFix(PsiMethod method) {
    myMethod = method;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("add.return.statement.family");
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.return.statement.text");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myMethod != null
        && myMethod.isValid()
        && myMethod.getManager().isInProject(myMethod)
        && myMethod.getBody() != null
        && myMethod.getBody().getRBrace() != null
        ;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myMethod.getContainingFile())) return;

    try {
      String value = suggestReturnValue();
      PsiElementFactory factory = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory();
      PsiReturnStatement returnStatement = (PsiReturnStatement) factory.createStatementFromText("return " + value+";", myMethod);
      PsiCodeBlock body = myMethod.getBody();
      returnStatement = (PsiReturnStatement) body.addBefore(returnStatement, body.getRBrace());

      TextRange range = returnStatement.getReturnValue().getTextRange();
      int offset = range.getStartOffset();
      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().setSelection(range.getEndOffset(), range.getStartOffset());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private String suggestReturnValue() {
    PsiType type = myMethod.getReturnType();
    // first try to find suitable local variable
    PsiVariable[] variables = getDeclaredVariables(myMethod);
    for (PsiVariable variable : variables) {
      if (variable.getType() != null
          && type.equals(variable.getType())) {
        return variable.getName();
      }
    }
    return PsiTypesUtil.getDefaultValueOfType(type);
  }

  private PsiVariable[] getDeclaredVariables(PsiMethod method) {
    List variables = new ArrayList();
    PsiStatement[] statements = method.getBody().getStatements();
    for (PsiStatement statement : statements) {
      if (statement instanceof PsiDeclarationStatement) {
        PsiElement[] declaredElements = ((PsiDeclarationStatement)statement).getDeclaredElements();
        for (PsiElement declaredElement : declaredElements) {
          if (declaredElement instanceof PsiLocalVariable) variables.add(declaredElement);
        }
      }
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    ContainerUtil.addAll(variables, parameters);
    return (PsiVariable[])variables.toArray(new PsiVariable[variables.size()]);
  }

  public boolean startInWriteAction() {
    return true;
  }

}
