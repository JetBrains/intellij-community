package com.intellij.openapi.samples;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public class HelloWorldAction extends AnAction {
  public void actionPerformed(AnActionEvent event) {
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    Messages.showMessageDialog(project, "Hello World!", "Information", Messages.getInformationIcon());
  }
}
