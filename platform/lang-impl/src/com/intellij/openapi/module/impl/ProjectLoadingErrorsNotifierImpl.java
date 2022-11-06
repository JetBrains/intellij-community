// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginFeatureService;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginSet;
import com.intellij.ide.plugins.advertiser.FeaturePluginData;
import com.intellij.ide.plugins.advertiser.PluginData;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.module.ConfigurationErrorDescription;
import com.intellij.openapi.module.ConfigurationErrorType;
import com.intellij.openapi.module.ProjectLoadingErrorsNotifier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.UnknownFeature;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.UnknownFeaturesCollector;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

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
      List<ConfigurationErrorDescription> descriptions = new ArrayList<>(entry.getValue());

      ConfigurationErrorType type = entry.getKey();
      String featureType = type.getFeatureType();
      if (featureType != null &&
          IdeaPluginDescriptorImpl.isOnDemandEnabled()) {
        descriptions.removeIf(isConfigurableLater(featureType));
      }
      if (descriptions.isEmpty()) {
        continue;
      }

      String invalidElements = type.getErrorText(descriptions.size(), descriptions.iterator().next().getElementName());
      String errorText = ProjectBundle.message("error.message.configuration.cannot.load", invalidElements);
      NotificationGroupManager.getInstance()
        .getNotificationGroup("Project Loading Error")
        .createNotification(ProjectBundle.message("notification.title.error.loading.project"),
                            errorText,
                            NotificationType.ERROR)
        .addAction(
          NotificationAction.create(ProjectBundle.message("error.message.configuration.cannot.load.button"), (event, notification) -> {
            List<ConfigurationErrorDescription> validDescriptions =
              ContainerUtil.findAll(descriptions, ConfigurationErrorDescription::isValid);
            if (RemoveInvalidElementsDialog.showDialog(myProject, CommonBundle.getErrorTitle(), type, invalidElements, validDescriptions)) {
              notification.expire();
            }
          }))
        .notify(myProject);
    }
  }

  private @NotNull Predicate<? super ConfigurationErrorDescription> isConfigurableLater(@NotNull @NonNls String featureType) {
    PluginSet pluginSet = PluginManagerCore.getPluginSet();
    PluginFeatureService pluginFeatureService = PluginFeatureService.getInstance();
    UnknownFeaturesCollector featuresCollector = UnknownFeaturesCollector.getInstance(myProject);

    Set<String> implementationNames = new LinkedHashSet<>();
    for (UnknownFeature unknownFeature : featuresCollector.getUnknownFeaturesOfType(featureType)) {
      String implementationName = unknownFeature.getImplementationName();
      FeaturePluginData featurePluginData = pluginFeatureService.getPluginForFeature(unknownFeature.getFeatureType(),
                                                                                     implementationName);
      if (featurePluginData == null) {
        continue;
      }

      PluginData pluginData = featurePluginData.getPluginData();
      // TODO is loadable on-demand (dependencies)
      IdeaPluginDescriptorImpl descriptor = pluginSet.findInstalledPlugin(pluginData.getPluginId());
      if (descriptor == null || !descriptor.isOnDemand()) {
        continue;
      }

      implementationNames.add(implementationName);
    }

    return description -> {
      String implementationName = description.getImplementationName();
      return implementationName != null && implementationNames.contains(implementationName);
    };
  }
}
