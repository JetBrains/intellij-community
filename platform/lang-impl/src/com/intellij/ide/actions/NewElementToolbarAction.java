package com.intellij.ide.actions;

import com.intellij.ide.DataManager;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;

/**
 * @author yole
 */
public class NewElementToolbarAction extends NewElementAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    if (e.getData(LangDataKeys.IDE_VIEW) == null) {
      final Project project = e.getData(PlatformDataKeys.PROJECT);
      final PsiFileSystemItem psiFile = e.getData(LangDataKeys.PSI_FILE).getParent();
      ProjectViewImpl.getInstance(project).selectCB(psiFile, psiFile.getVirtualFile(), true).doWhenDone(new Runnable() {
        public void run() {
          showPopup(DataManager.getInstance().getDataContext());
        }
      });
    }
    else {
      super.actionPerformed(e);
    }
  }

  @Override
  public void update(AnActionEvent event) {
    super.update(event);
    if (event.getData(LangDataKeys.IDE_VIEW) == null) {
      Project project = event.getData(PlatformDataKeys.PROJECT);
      PsiFile psiFile = event.getData(LangDataKeys.PSI_FILE);
      if (project != null && psiFile != null) {
        final ToolWindow projectViewWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
        if (projectViewWindow.isVisible()) {
          event.getPresentation().setEnabled(true);
        }
      }
    }
  }
}
