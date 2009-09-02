/*
 * User: anna
 * Date: 28-Jun-2007
 */
package com.intellij.internal;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

public class DumpConfigurationTypesAction extends AnAction implements DumbAware {
  public DumpConfigurationTypesAction() {
    super("Dump Configurations");
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final ConfigurationType[] factories =
      RunManager.getInstance(project).getConfigurationFactories();
    for (ConfigurationType factory : factories) {
      System.out.println(factory.getDisplayName() + " : " + factory.getId());
    }
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(PlatformDataKeys.PROJECT) != null);
  }
}