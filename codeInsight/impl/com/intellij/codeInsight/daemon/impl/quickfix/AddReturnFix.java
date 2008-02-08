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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AddReturnFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddReturnFix");
  private final PsiMethod myMethod;

  public AddReturnFix(PsiMethod method) {
    myMethod = method;
  }

  public String getText() {
    return QuickFixBundle.message("add.return.statement.family");
  }

  public String getFamilyName() {
    return QuickFixBundle.message("add.return.statement.text");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myMethod != null
        && myMethod.isValid()
        && myMethod.getManager().isInProject(myMethod)
        && myMethod.getBody() != null
        && myMethod.getBody().getRBrace() != null
        ;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
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
    variables.addAll(Arrays.asList(parameters));
    return (PsiVariable[]) variables.toArray(new PsiVariable[variables.size()]);
  }

  public boolean startInWriteAction() {
    return true;
  }

}
