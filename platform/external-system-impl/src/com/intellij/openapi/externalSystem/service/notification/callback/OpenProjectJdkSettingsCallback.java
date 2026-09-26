// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.notification.callback;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public class OpenProjectJdkSettingsCallback extends NotificationListener.Adapter {

  public static final String ID = "#open_project_jdk_settings";
  private final Project myProject;

  public OpenProjectJdkSettingsCallback(Project project) {
    myProject = project;
  }

  @Override
  protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
    ProjectSettingsService.getInstance(myProject).openProjectSettings();
  }
}
