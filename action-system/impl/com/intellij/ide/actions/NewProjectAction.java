
package com.intellij.ide.actions;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;

/**
 *
 */
public class NewProjectAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    ProjectUtil.createNewProject(PlatformDataKeys.PROJECT.getData(e.getDataContext()), null);
  }
}
