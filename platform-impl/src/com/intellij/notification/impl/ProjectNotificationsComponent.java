package com.intellij.notification.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class ProjectNotificationsComponent implements ProjectComponent {
  private Project myProject;
  private StatusBarEx myStatusBar;

  public ProjectNotificationsComponent(final Project project) {
    myProject = project;
  }

  public void projectOpened() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    myStatusBar = (StatusBarEx) WindowManager.getInstance().getStatusBar(myProject);
    myStatusBar.getNotificationArea().connect(myProject);
  }

  public void projectClosed() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    myStatusBar.getNotificationArea().getModel().clearProjectNotifications();
    myProject = null;
  }

  @NotNull
  public String getComponentName() {
    return "Project Notifications";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
