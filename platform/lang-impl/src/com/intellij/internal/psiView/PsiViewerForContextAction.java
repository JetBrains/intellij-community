package com.intellij.internal.psiView;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Created by IntelliJ IDEA.
 * User: Nadya.Zabrodina
 * Date: 7/4/11
 * Time: 4:16 PM
 */
public class PsiViewerForContextAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {

    Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
    PsiFile currentFile = LangDataKeys.PSI_FILE.getData(e.getDataContext());
    new PsiViewerDialog(currentFile.getProject(), false, currentFile, editor).show();
  }

  @Override
  public void update(AnActionEvent e) {
    if (!ApplicationManagerEx.getApplicationEx().isInternal()) {
      e.getPresentation().setVisible(false);
      e.getPresentation().setEnabled(false);
      return;
    }
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    PsiFile currentFile = LangDataKeys.PSI_FILE.getData(e.getDataContext());
    e.getPresentation().setEnabled(project != null && currentFile != null);
  }
}
