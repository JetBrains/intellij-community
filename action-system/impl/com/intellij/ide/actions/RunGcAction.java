/**
 * @author Vladimir Kondratyev
 */
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class RunGcAction extends AnAction{
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
