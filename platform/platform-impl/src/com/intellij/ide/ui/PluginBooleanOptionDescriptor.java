// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.newui.MyPluginModel;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.NotABooleanOptionDescription;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.nio.file.FileVisitResult;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Konstantin Bulenkov
 */
public final class PluginBooleanOptionDescriptor extends BooleanOptionDescription
  implements BooleanOptionDescription.RequiresRebuild,
             NotABooleanOptionDescription {

  private static final AtomicReference<Notification> ourPreviousNotification = new AtomicReference<>();

  private final IdeaPluginDescriptor myDescriptor;

  PluginBooleanOptionDescriptor(@NotNull IdeaPluginDescriptor descriptor) {
    super(IdeBundle.message("search.everywhere.command.plugins", descriptor.getName()),
          PluginManagerConfigurable.ID);
    myDescriptor = descriptor;
  }

  @Override
  public boolean isOptionEnabled() {
    return !PluginEnabler.HEADLESS.isDisabled(myDescriptor.getPluginId());
  }

  @Override
  public void setOptionState(boolean enable) {
    togglePluginState(List.of(myDescriptor),
                      enable);
  }

  public static void togglePluginState(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                       boolean enable) {
    if (descriptors.isEmpty()) {
      return;
    }

    Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = PluginManagerCore.buildPluginIdMap();
    Collection<? extends IdeaPluginDescriptor> autoSwitchedDescriptors = enable ?
                                                                         getDependenciesToEnable(descriptors, pluginIdMap) :
                                                                         getDependentsToDisable(descriptors, pluginIdMap);

    PluginEnabler pluginEnabler = PluginEnabler.getInstance();
    boolean appliedWithoutRestart = enable ?
                                    pluginEnabler.enable(autoSwitchedDescriptors) :
                                    pluginEnabler.disable(autoSwitchedDescriptors);

    if (autoSwitchedDescriptors.size() > descriptors.size()) {
      String content =
        IdeBundle.message(enable ? "plugins.auto.enabled.notification.content" : "plugins.auto.disabled.notification.content",
                          MyPluginModel.joinPluginNamesOrIds(MyPluginModel.getPluginNames(descriptors)),
                          MyPluginModel.joinPluginNamesOrIds(MyPluginModel.getPluginNames(autoSwitchedDescriptors)));

      showAutoSwitchNotification(autoSwitchedDescriptors, pluginEnabler, content, enable);
    }

    notifyIfRestartRequired(!appliedWithoutRestart);
  }

  private static void showAutoSwitchNotification(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                                 @NotNull PluginEnabler pluginEnabler,
                                                 @NotNull @Nls String content,
                                                 boolean enabled) {
    String title = IdeBundle.message(enabled ? "plugins.auto.enabled.notification.title" : "plugins.auto.disabled.notification.title");
    Notification switchNotification = UpdateChecker.getNotificationGroupForPluginUpdateResults()
      .createNotification(content, NotificationType.INFORMATION)
      .setDisplayId("plugin.auto.switch")
      .setTitle(title)
      .addAction(new NotificationAction(IdeBundle.message("plugins.auto.switch.action.name")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e,
                                    @NotNull Notification notification) {
          boolean appliedWithoutRestart = enabled ?
                                          pluginEnabler.disable(descriptors) :
                                          pluginEnabler.enable(descriptors);
          notification.expire();

          notifyIfRestartRequired(!appliedWithoutRestart);
        }
      });

    Set<PluginId> pluginIds = IdeaPluginDescriptorImplKt.toPluginSet(descriptors);

    DisabledPluginsState.addDisablePluginListener(new Runnable() {
      @Override
      public void run() {
        Condition<? super PluginId> condition = pluginEnabler::isDisabled;
        boolean notificationValid = enabled ?
                                    !ContainerUtil.exists(pluginIds, condition) :
                                    ContainerUtil.and(pluginIds, condition);
        if (!notificationValid) {
          switchNotification.expire();
        }

        Balloon balloon = switchNotification.getBalloon();
        if (balloon == null || balloon.isDisposed()) {
          ApplicationManager.getApplication().invokeLater(() -> {
            DisabledPluginsState.removeDisablePluginListener(this);
          });
        }
      }
    });
    switchNotification.notify(null);
  }

  private static @NotNull Collection<? extends IdeaPluginDescriptor> getDependenciesToEnable(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                                                                             @NotNull Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap) {
    Set<IdeaPluginDescriptor> result = new LinkedHashSet<>();

    for (IdeaPluginDescriptor descriptor : descriptors) {
      result.add(descriptor);

      if (!(descriptor instanceof IdeaPluginDescriptorImpl)) {
        continue;
      }

      PluginManagerCore.processAllNonOptionalDependencies((IdeaPluginDescriptorImpl)descriptor, pluginIdMap, dependency ->
        PluginManagerCore.CORE_ID.equals(dependency.getPluginId()) ||
        dependency.isEnabled() ||
        !result.add(dependency) ?
        FileVisitResult.SKIP_SUBTREE /* if descriptor has already been added/enabled, no need to process it's dependencies */ :
        FileVisitResult.CONTINUE);
    }

    return Collections.unmodifiableSet(result);
  }

  private static @NotNull Collection<? extends IdeaPluginDescriptor> getDependentsToDisable(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                                                                            @NotNull Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap) {
    Set<IdeaPluginDescriptor> result = new LinkedHashSet<>();
    ApplicationInfoEx applicationInfo = ApplicationInfoEx.getInstanceEx();

    for (IdeaPluginDescriptor descriptor : descriptors) {
      result.add(descriptor);

      result.addAll(MyPluginModel.getDependents(descriptor, applicationInfo, pluginIdMap));
    }

    return Collections.unmodifiableSet(result);
  }

  private static void notifyIfRestartRequired(boolean restartRequired) {
    if (!restartRequired) {
      return;
    }

    Notification notification = ourPreviousNotification.get();
    if (notification == null) {
      return;
    }

    Balloon balloon = notification.getBalloon();
    if (balloon != null && !balloon.isDisposed()) {
      return;
    }

    Notification newNotification = UpdateChecker.getNotificationGroupForIdeUpdateResults()
      .createNotification(
        IdeBundle.message("plugins.changed.notification.content", ApplicationNamesInfo.getInstance().getFullProductName()),
        NotificationType.INFORMATION)
      .setTitle(IdeBundle.message("plugins.changed.notification.title"))
      .setDisplayId("plugins.updated.restart.required")
      .addAction(new DumbAwareAction(IdeBundle.message("ide.restart.action")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          ApplicationManager.getApplication().restart();
        }
      });

    if (ourPreviousNotification.compareAndSet(notification, newNotification)) {
      newNotification.notify(null);
    }
  }
}