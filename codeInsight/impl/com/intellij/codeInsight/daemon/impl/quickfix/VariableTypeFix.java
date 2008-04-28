package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class VariableTypeFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.VariableTypeFix");

  private final PsiVariable myVariable;
  private final PsiType myReturnType;

  public VariableTypeFix(PsiVariable variable, PsiType toReturn) {
    myVariable = variable;
    myReturnType = toReturn != null ? GenericsUtil.getVariableTypeByExpressionType(toReturn) : null;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("fix.variable.type.text",
                                  myVariable.getName(),
                                  myReturnType.getCanonicalText());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.variable.type.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myVariable != null
        && myVariable.isValid()
        && myVariable.getManager().isInProject(myVariable)
        && myReturnType != null
        && myReturnType.isValid()
        && !TypeConversionUtil.isNullType(myReturnType)
        && !TypeConversionUtil.isVoidType(myReturnType);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myVariable.getContainingFile())) return;
    try {
      myVariable.normalizeDeclaration();
      myVariable.getTypeElement().replace(JavaPsiFacade.getInstance(file.getProject()).getElementFactory().createTypeElement(myReturnType));
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(myVariable);
      UndoUtil.markPsiFileForUndo(file);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
