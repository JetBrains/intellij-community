package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.IdeConfigurablesGroup;
import com.intellij.openapi.options.ex.ProjectConfigurablesGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import javax.swing.*;

public class ShowSettingsAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }

    ConfigurableGroup[] group = new ConfigurableGroup[]{
      new ProjectConfigurablesGroup(project, false),
      new IdeConfigurablesGroup()
    };

    final long startTime = System.nanoTime();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        final long endTime = System.nanoTime();
        System.out.println("Displaying settings dialog took " + ((endTime - startTime) / 1000000) + " ms");
      }
    });
    ShowSettingsUtil.getInstance().showSettingsDialog(project, group);
  }
}
