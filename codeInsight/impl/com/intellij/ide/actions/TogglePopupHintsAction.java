/**
 * @author Vladimir Kondratyev
 */
package com.intellij.ide.actions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

public class TogglePopupHintsAction extends AnAction{
  private static final Logger LOG=Logger.getInstance("#com.intellij.ide.actions.TogglePopupHintsAction");

  private static PsiFile getTargetFile(DataContext dataContext){
    Project project = DataKeys.PROJECT.getData(dataContext);
    if(project==null){
      return null;
    }
    VirtualFile[] files=FileEditorManager.getInstance(project).getSelectedFiles();
    if(files.length==0){
      return null;
    }
    PsiFile psiFile=PsiManager.getInstance(project).findFile(files[0]);
    LOG.assertTrue(psiFile!=null);
    return psiFile;
  }

  public void update(AnActionEvent e){
    PsiFile psiFile=getTargetFile(e.getDataContext());
    e.getPresentation().setEnabled(psiFile!=null);
  }

  public void actionPerformed(AnActionEvent e){
    PsiFile psiFile=getTargetFile(e.getDataContext());
    LOG.assertTrue(psiFile!=null);
    Project project = DataKeys.PROJECT.getData(e.getDataContext());
    LOG.assertTrue(project!=null);
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    codeAnalyzer.setImportHintsEnabled(psiFile,!codeAnalyzer.isImportHintsEnabled(psiFile));
    ((StatusBarEx) WindowManager.getInstance().getStatusBar(project)).updateEditorHighlightingStatus(false);
  }
}
