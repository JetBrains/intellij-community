package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiParameter;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class MakeVarargParameterLastFix implements IntentionAction {
  public MakeVarargParameterLastFix(PsiParameter parameter) {
    myParameter = parameter;
  }

  private PsiParameter myParameter;

  @NotNull
  public String getText() {
    return QuickFixBundle.message("make.vararg.parameter.last.text", myParameter.getName());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("make.vararg.parameter.last.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myParameter.isValid() && myParameter.getManager().isInProject(myParameter);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myParameter.getParent().add(myParameter);
    myParameter.delete();
  }

  public boolean startInWriteAction() {
    return true;
  }
}
