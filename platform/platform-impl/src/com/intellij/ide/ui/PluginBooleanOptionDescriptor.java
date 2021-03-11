// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.NotABooleanOptionDescription;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.FileVisitResult;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Konstantin Bulenkov
 */
final class PluginBooleanOptionDescriptor extends BooleanOptionDescription implements BooleanOptionDescription.RequiresRebuild, NotABooleanOptionDescription {
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
  public void setOptionState(boolean enabled) {
    Set<IdeaPluginDescriptor> autoSwitchedIds = enabled ?
                                                getPluginsIdsToEnable(plugin) :
                                                getPluginsIdsToDisable(plugin);
    boolean enabledWithoutRestart = ProjectPluginTrackerManager.getInstance().updatePluginsState(
      autoSwitchedIds,
      PluginEnableDisableAction.globally(enabled)
    );

    if (autoSwitchedIds.size() > 1) {
      showAutoSwitchNotification(autoSwitchedIds, enabled);
    }

    if (!enabledWithoutRestart) {
      ourRestartNeededNotifier.showNotification();
    }
  }

  private void showAutoSwitchNotification(@NotNull Collection<? extends IdeaPluginDescriptor> autoSwitchedPlugins, boolean enabled) {
    StringBuilder builder = new StringBuilder();
    for (IdeaPluginDescriptor autoSwitchedPlugin : autoSwitchedPlugins) {
      if (builder.length() > 0) {
        builder.append(", ");
      }
      builder.append('"').append(autoSwitchedPlugin.getName()).append('"');
    }
    String dependenciesString = builder.toString();

    String titleKey = enabled ? "plugins.auto.enabled.notification.title" : "plugins.auto.disabled.notification.title";
    String contentKey = enabled ? "plugins.auto.enabled.notification.content" : "plugins.auto.disabled.notification.content";
    String pluginString = '"' + getOption() + '"';
    Notification switchNotification = NotificationGroupManager.getInstance().getNotificationGroup("Plugins AutoSwitch")
      .createNotification(IdeBundle.message(contentKey, pluginString, dependenciesString), NotificationType.INFORMATION)
      .setTitle(IdeBundle.message(titleKey))
      .addAction(new UndoPluginsSwitchAction(autoSwitchedPlugins, enabled));

    DisabledPluginsState.addDisablePluginListener(new Runnable() {
      @Override
      public void run() {
        boolean notificationValid = enabled ?
                                    !ContainerUtil.exists(autoSwitchedPlugins, descriptor -> PluginManagerCore.isDisabled(descriptor.getPluginId()))
                                    : ContainerUtil.and(autoSwitchedPlugins, descriptor -> PluginManagerCore.isDisabled(descriptor.getPluginId()));
        if (!notificationValid) {
          switchNotification.expire();
        }

        Balloon balloon = switchNotification.getBalloon();
        if (balloon == null || balloon.isDisposed()) {
          ApplicationManager.getApplication().invokeLater(() -> DisabledPluginsState.removeDisablePluginListener(this));
        }
      }
    });
    Notifications.Bus.notify(switchNotification);
  }

  private static @NotNull Set<IdeaPluginDescriptor> getPluginsIdsToEnable(@NotNull IdeaPluginDescriptor rootDescriptor) {
    Set<IdeaPluginDescriptor> result = new HashSet<>();
    result.add(rootDescriptor);

    if (rootDescriptor instanceof IdeaPluginDescriptorImpl) {
      PluginManagerCore.processAllDependencies(
        (IdeaPluginDescriptorImpl)rootDescriptor,
        false,
        descriptor -> descriptor.getPluginId() != PluginManagerCore.CORE_ID &&
                      !descriptor.isEnabled() &&
                      result.add(descriptor) ?
                      FileVisitResult.CONTINUE : // if descriptor has already been added/enabled, no need to process it's dependencies
                      FileVisitResult.SKIP_SUBTREE
      );
    }

    return result;
  }

  private static @NotNull Set<IdeaPluginDescriptor> getPluginsIdsToDisable(@NotNull IdeaPluginDescriptor rootDescriptor) {
    Set<IdeaPluginDescriptor> result = new HashSet<>();
    result.add(rootDescriptor);

    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    PluginId rootId = rootDescriptor.getPluginId();

    for (IdeaPluginDescriptor plugin : PluginManagerCore.getPlugins()) {
      PluginId pluginId = plugin.getPluginId();
      if (pluginId == rootId || appInfo.isEssentialPlugin(pluginId) || !plugin.isEnabled() || plugin.isImplementationDetail()) {
        continue;
      }

      if (!(plugin instanceof IdeaPluginDescriptorImpl)) {
        continue;
      }

      IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)plugin;
      if (pluginDescriptor.isDeleted()) {
        continue;
      }

      PluginManagerCore.processAllDependencies(pluginDescriptor, false, descriptor -> {
        if (descriptor.getPluginId() == rootId) {
          result.add(plugin);
          return FileVisitResult.TERMINATE;
        }
        return FileVisitResult.CONTINUE;
      });
    }
    return result;
  }

  private static final class UndoPluginsSwitchAction extends NotificationAction {
    private final @NotNull Collection<? extends IdeaPluginDescriptor> myDescriptors;
    private final boolean myEnabled;

    UndoPluginsSwitchAction(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors, boolean enabled) {
      super(IdeBundle.message("plugins.auto.switch.action.name"));

      myDescriptors = descriptors;
      myEnabled = enabled;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
      boolean enabled = !myEnabled;
      DisabledPluginsState.enablePlugins(myDescriptors, enabled);
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
        .addAction(new AnAction(IdeBundle.message("ide.restart.action")) {
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