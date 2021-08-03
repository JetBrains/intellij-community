// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.newui.MyPluginModel;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.NotABooleanOptionDescription;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.Balloon;
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
public final class PluginBooleanOptionDescriptor extends BooleanOptionDescription implements BooleanOptionDescription.RequiresRebuild, NotABooleanOptionDescription {
  private static final Notifier ourRestartNeededNotifier = new Notifier();

  private final IdeaPluginDescriptor plugin;

  PluginBooleanOptionDescriptor(@NotNull IdeaPluginDescriptor descriptor) {
    super(IdeBundle.message("search.everywhere.command.plugins", descriptor.getName()), PluginManagerConfigurable.ID);

    plugin = descriptor;
  }

  @Override
  public boolean isOptionEnabled() {
    return plugin.isEnabled();
  }

  @Override
  public void setOptionState(boolean enable) {
    togglePluginState(List.of(plugin),
                      PluginEnableDisableAction.globally(enable));
  }

  public static void togglePluginState(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                       @NotNull PluginEnableDisableAction action) {
    if (descriptors.isEmpty()) {
      return;
    }

    boolean enable = action.isEnable();
    Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap = PluginManagerCore.buildPluginIdMap();
    Collection<? extends IdeaPluginDescriptor> autoSwitchedDescriptors = enable ?
                                                                         getDependenciesToEnable(descriptors, pluginIdMap) :
                                                                         getDependentsToDisable(descriptors, pluginIdMap);

    boolean enabledWithoutRestart = ProjectPluginTrackerManager.getInstance()
      .updatePluginsState(autoSwitchedDescriptors, action);

    if (autoSwitchedDescriptors.size() > descriptors.size()) {
      String content =
        IdeBundle.message(enable ? "plugins.auto.enabled.notification.content" : "plugins.auto.disabled.notification.content",
                          MyPluginModel.joinPluginNamesOrIds(MyPluginModel.getPluginNames(descriptors)),
                          MyPluginModel.joinPluginNamesOrIds(MyPluginModel.getPluginNames(autoSwitchedDescriptors)));

      showAutoSwitchNotification(autoSwitchedDescriptors, content, enable);
    }

    if (!enabledWithoutRestart) {
      ourRestartNeededNotifier.showNotification();
    }
  }

  private static void showAutoSwitchNotification(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                                 @NotNull @Nls String content,
                                                 boolean enabled) {
    String title = IdeBundle.message(enabled ? "plugins.auto.enabled.notification.title" : "plugins.auto.disabled.notification.title");
    Notification switchNotification = NotificationGroupManager.getInstance()
      .getNotificationGroup("Plugins AutoSwitch")
      .createNotification(content, NotificationType.INFORMATION)
      .setTitle(title)
      .addAction(new UndoPluginsSwitchAction(descriptors, PluginEnableDisableAction.globally(!enabled)));

    List<PluginId> pluginIds = ContainerUtil.map(descriptors,
                                                 IdeaPluginDescriptor::getPluginId);

    DisabledPluginsState.addDisablePluginListener(new Runnable() {
      @Override
      public void run() {
        Condition<? super PluginId> condition = PluginManagerCore::isDisabled;
        boolean notificationValid = enabled ?
                                    !ContainerUtil.exists(pluginIds, condition) :
                                    ContainerUtil.and(pluginIds, condition);
        if (!notificationValid) {
          switchNotification.expire();
        }

        Balloon balloon = switchNotification.getBalloon();
        if (balloon == null || balloon.isDisposed()) {
          ApplicationManager.getApplication().invokeLater(() -> DisabledPluginsState.removeDisablePluginListener(this));
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

      if (descriptor instanceof IdeaPluginDescriptorImpl) {
        PluginManagerCore.processAllNonOptionalDependencies((IdeaPluginDescriptorImpl)descriptor, pluginIdMap, dependency ->
          PluginManagerCore.CORE_ID.equals(dependency.getPluginId()) ||
          dependency.isEnabled() ||
          !result.add(dependency) ?
          FileVisitResult.SKIP_SUBTREE /* if descriptor has already been added/enabled, no need to process it's dependencies */ :
          FileVisitResult.CONTINUE);
      }
    }

    return Collections.unmodifiableSet(result);
  }

  private static @NotNull Collection<? extends IdeaPluginDescriptor> getDependentsToDisable(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                                                                                            @NotNull Map<PluginId, IdeaPluginDescriptorImpl> pluginIdMap) {
    Set<IdeaPluginDescriptor> result = new LinkedHashSet<>();
    ApplicationInfoEx applicationInfo = ApplicationInfoEx.getInstanceEx();

    for (IdeaPluginDescriptor descriptor : descriptors) {
      result.addAll(MyPluginModel.getDependents(descriptor,
                                                pluginIdMap,
                                                applicationInfo));
    }

    return Collections.unmodifiableSet(result);
  }

  private static final class UndoPluginsSwitchAction extends NotificationAction {

    private final @NotNull Collection<? extends IdeaPluginDescriptor> myDescriptors;
    private final @NotNull PluginEnableDisableAction myAction;

    UndoPluginsSwitchAction(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors,
                            @NotNull PluginEnableDisableAction action) {
      super(IdeBundle.message("plugins.auto.switch.action.name"));

      myDescriptors = descriptors;
      myAction = action;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
      PluginEnabler.HEADLESS.setEnabledState(myDescriptors, myAction);
      notification.expire();
      ourRestartNeededNotifier.showNotification();
    }
  }

  private static final class Notifier {
    private final AtomicReference<Notification> prevNotification = new AtomicReference<>();

    public void showNotification() {
      Notification prev = prevNotification.get();

      if (prev != null && prev.getBalloon() != null && !prev.getBalloon().isDisposed()) {
        return;
      }

      Notification next = NotificationGroupManager.getInstance().getNotificationGroup("Plugins updates")
        .createNotification(
          IdeBundle.message("plugins.changed.notification.content", ApplicationNamesInfo.getInstance().getFullProductName()),
          NotificationType.INFORMATION)
        .setTitle(IdeBundle.message("plugins.changed.notification.title"))
        .addAction(new DumbAwareAction(IdeBundle.message("ide.restart.action")) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            ApplicationManager.getApplication().restart();
          }
        });

      if (prevNotification.compareAndSet(prev, next)) {
        Notifications.Bus.notify(next);
      }
    }
  }
}