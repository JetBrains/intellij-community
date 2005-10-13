package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

public class ImplementMethodsFix implements IntentionAction {
  private final PsiClass myClass;

  public ImplementMethodsFix(PsiClass aClass) {
    myClass = aClass;
  }

  public String getText() {
    return QuickFixBundle.message("implement.methods.fix");
  }

  public String getFamilyName() {
    return QuickFixBundle.message("implement.methods.fix");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myClass.isValid() && myClass.getManager().isInProject(myClass);
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myClass.getContainingFile())) return;
    OverrideImplementUtil.chooseAndImplementMethods(project, editor, myClass);
  }

  public boolean startInWriteAction() {
    return false;
  }

}
