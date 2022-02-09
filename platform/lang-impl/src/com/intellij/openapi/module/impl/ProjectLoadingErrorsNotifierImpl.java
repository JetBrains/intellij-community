// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl;

import com.intellij.CommonBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.module.ConfigurationErrorDescription;
import com.intellij.openapi.module.ConfigurationErrorType;
import com.intellij.openapi.module.ProjectLoadingErrorsNotifier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// overridden in Rider
public class ProjectLoadingErrorsNotifierImpl extends ProjectLoadingErrorsNotifier {
  private final MultiMap<ConfigurationErrorType, ConfigurationErrorDescription> myErrors = new MultiMap<>();
  private final Object myLock = new Object();
  private final Project myProject;

  public ProjectLoadingErrorsNotifierImpl(Project project) {
    myProject = project;
  }

  @Override
  public void registerError(@NotNull ConfigurationErrorDescription errorDescription) {
    registerErrors(Collections.singletonList(errorDescription));
  }

  @Override
  public void registerErrors(@NotNull Collection<? extends ConfigurationErrorDescription> errorDescriptions) {
    if (myProject.isDisposed() || myProject.isDefault() || errorDescriptions.isEmpty()) {
      return;
    }

    boolean first;
    synchronized (myLock) {
      first = myErrors.isEmpty();
      for (ConfigurationErrorDescription description : errorDescriptions) {
        myErrors.putValue(description.getErrorType(), description);
      }
    }
    if (myProject.isInitialized()) {
      fireNotifications();
    }
    else if (first) {
      StartupManager.getInstance(myProject).runAfterOpened(() -> fireNotifications());
    }
  }

  private void fireNotifications() {
    Map<ConfigurationErrorType, Collection<ConfigurationErrorDescription>> descriptionMap;
    synchronized (myLock) {
      if (myErrors.isEmpty()) {
        return;
      }
      descriptionMap = myErrors.toHashMap();
      myErrors.clear();
    }

    for (Map.Entry<ConfigurationErrorType, Collection<ConfigurationErrorDescription>> entry : descriptionMap.entrySet()) {
      Collection<ConfigurationErrorDescription> descriptions = entry.getValue();
      if (descriptions.isEmpty()) {
        continue;
      }

      ConfigurationErrorType type = entry.getKey();
      String invalidElements = type.getErrorText(descriptions.size(), descriptions.iterator().next().getElementName());
      String errorText = ProjectBundle.message("error.message.configuration.cannot.load", invalidElements);
      NotificationGroupManager.getInstance().getNotificationGroup("Project Loading Error").createNotification(
                       ProjectBundle.message("notification.title.error.loading.project"),
                       errorText,
                       NotificationType.ERROR)
        .setListener((notification, event) -> {
          List<ConfigurationErrorDescription> validDescriptions = ContainerUtil.findAll(descriptions, ConfigurationErrorDescription::isValid);
          if (RemoveInvalidElementsDialog.showDialog(myProject, CommonBundle.getErrorTitle(), type, invalidElements, validDescriptions)) {
            notification.expire();
          }
        })
        .notify(myProject);
    }
  }
}
