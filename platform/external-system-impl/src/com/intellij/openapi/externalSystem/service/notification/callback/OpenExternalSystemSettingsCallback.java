// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.notification.callback;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.externalSystem.ExternalSystemConfigurableAware;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalSystemConfigurable;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;

/**
 * @author Vladislav.Soroka
 */
public class OpenExternalSystemSettingsCallback extends NotificationListener.Adapter {

  public static final String ID = "#open_external_system_settings";
  private final Project myProject;
  private final @NotNull ProjectSystemId mySystemId;
  private final @Nullable String myLinkedProjectPath;

  public OpenExternalSystemSettingsCallback(Project project, @NotNull ProjectSystemId systemId) {
    this(project, systemId, null);
  }

  public OpenExternalSystemSettingsCallback(Project project, @NotNull ProjectSystemId systemId, @Nullable String linkedProjectPath) {
    myProject = project;
    mySystemId = systemId;
    myLinkedProjectPath = linkedProjectPath;
  }

  @Override
  protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {

    ExternalSystemManager<?, ?, ?, ?, ?> manager;
    if (myLinkedProjectPath == null ||
        !((manager = ExternalSystemApiUtil.getManager(mySystemId)) instanceof ExternalSystemConfigurableAware)) {
      ShowSettingsUtil.getInstance().showSettingsDialog(myProject, mySystemId.getReadableName());
      return;
    }
    final Configurable configurable = ((ExternalSystemConfigurableAware)manager).getConfigurable(myProject);
    if(configurable instanceof AbstractExternalSystemConfigurable) {
      ShowSettingsUtil.getInstance().editConfigurable(myProject, configurable,
                                                      () -> ((AbstractExternalSystemConfigurable<?, ?, ?>)configurable).selectProject(myLinkedProjectPath));
    }

  }
}
