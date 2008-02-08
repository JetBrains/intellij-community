package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;

public class OptimizeImportsFix implements IntentionAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.OptimizeImportsFix");

  public String getText() {
    return QuickFixBundle.message("optimize.imports.fix");
  }

  public String getFamilyName() {
    return QuickFixBundle.message("optimize.imports.fix");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return file.getManager().isInProject(file) && file instanceof PsiJavaFile;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return;
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

    try{
      JavaCodeStyleManager.getInstance(project).optimizeImports(file);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
