package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

public class EnableOptimizeImportsOnTheFlyFix implements IntentionAction{
  @NotNull
  public String getText() {
    return QuickFixBundle.message("enable.optimize.imports.on.the.fly");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return file.getManager().isInProject(file)
           && file instanceof PsiJavaFile
           && !CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY
      ;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = true;
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  public boolean startInWriteAction() {
    return true;
  }
}
