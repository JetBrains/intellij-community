package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AddVariableInitializerFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddReturnFix");
  private final PsiVariable myVariable;

  public AddVariableInitializerFix(PsiVariable variable) {
    myVariable = variable;
  }

  @NotNull
  public String getText() {
    return CodeInsightBundle.message("quickfix.add.variable.text", myVariable.getName());
  }

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("quickfix.add.variable.family.name");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myVariable != null
        && myVariable.isValid()
        && myVariable.getManager().isInProject(myVariable)
        && !myVariable.hasInitializer()
        ;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(myVariable.getContainingFile())) return;

    String initializerText = suggestInitializer();
    PsiElementFactory factory = myVariable.getManager().getElementFactory();
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
    CodeInsightUtil.forcePsiPostprocessAndRestoreElement(myVariable);
    TextRange range = myVariable.getInitializer().getTextRange();
    int offset = range.getStartOffset();
    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
  }

  private String suggestInitializer() {
    PsiType type = myVariable.getType();
    return PsiTypesUtil.getDefaultValueOfType(type);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
