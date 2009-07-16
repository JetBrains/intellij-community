package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class ToggleShowImportPopupsAction extends ToggleAction {

  @Override
  public boolean isSelected(AnActionEvent e) {
    return getAnalyzer(e).isImportHintsEnabled(getFile(e));
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    getAnalyzer(e).setImportHintsEnabled(getFile(e), state);
  }

  @Override
  public void update(AnActionEvent e) {
    if (getFile(e) == null) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
    }
    else {
      e.getPresentation().setEnabled(true);
      e.getPresentation().setVisible(true);
      super.update(e);
    }
  }

  private DaemonCodeAnalyzer getAnalyzer(AnActionEvent e) {
    return DaemonCodeAnalyzer.getInstance(e.getData(PlatformDataKeys.PROJECT));
  }

  @Nullable
  private static PsiFile getFile(AnActionEvent e) {
    Editor editor = e.getData(PlatformDataKeys.EDITOR);
    return editor == null ? null : e.getData(LangDataKeys.PSI_FILE);
  }
}
