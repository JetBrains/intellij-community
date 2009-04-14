/**
 * @author Vladimir Kondratyev
 */
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;

public class RunGcAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e){
    /*
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project != null){
      ((PsiManagerImpl)PsiManager.getInstance(project)).getMemoryManager().releaseCodeBlocks();
    }
    */
    System.gc();
  }
}
