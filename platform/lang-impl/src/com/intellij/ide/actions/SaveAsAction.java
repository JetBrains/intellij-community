package com.intellij.ide.actions;

import com.intellij.ide.util.PlatformPackageUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.copy.CopyHandler;

public class SaveAsAction extends DumbAwareAction {

  @Override
  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    e.getPresentation().setEnabled(project!=null && virtualFile!=null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    @SuppressWarnings({"ConstantConditions"}) final PsiElement element = PsiManager.getInstance(project).findFile(virtualFile);
    if(element==null) return;
    CopyHandler.doCopy(new PsiElement[] {element.getContainingFile()}, PlatformPackageUtil.getDirectory(element));
  }
}
