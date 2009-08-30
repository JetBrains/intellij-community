package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.actions.TypeCookAction;
import org.jetbrains.annotations.NotNull;

public class GenerifyFileFix implements IntentionAction {
  private final PsiFile myFile;

  public GenerifyFileFix(PsiFile file) {
    myFile = file;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("generify.text", myFile.getName());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("generify.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myFile.isValid() && PsiManager.getInstance(project).isInProject(myFile);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myFile)) return;
    new TypeCookAction().getHandler().invoke(project, editor, file, null);
  }

  public boolean startInWriteAction() {
    return false;
  }
}
