// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.popup.Balloon;
import org.jetbrains.annotations.NotNull;

import java.nio.file.FileVisitResult;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * @author Konstantin Bulenkov
 */
final class PluginBooleanOptionDescriptor extends BooleanOptionDescription {
  private static final NotificationGroup PLUGINS_LIST_CHANGED_GROUP =
    new NotificationGroup("Plugins updates", NotificationDisplayType.STICKY_BALLOON, false);
  private static final NotificationGroup PLUGINS_AUTO_SWITCH_GROUP =
    new NotificationGroup("Plugins AutoSwitch", NotificationDisplayType.BALLOON, false);

  private static final Notifier ourRestartNeededNotifier = new Notifier();

  private final IdeaPluginDescriptor plugin;

  PluginBooleanOptionDescriptor(@NotNull IdeaPluginDescriptor descriptor) {
    super(descriptor.getName(), PluginManagerConfigurable.ID);

    plugin = descriptor;
  }

  @Override
  public boolean isOptionEnabled() {
    return plugin.isEnabled();
  }

  @Override
  public void setOptionState(boolean enabled) {
    Collection<IdeaPluginDescriptor> autoSwitchedIds = enabled ? getPluginsIdsToEnable(plugin) : getPluginsIdsToDisable(plugin);
    PluginManager.getInstance().enablePlugins(autoSwitchedIds, enabled);
    if (autoSwitchedIds.size() > 1) {
      showAutoSwitchNotification(autoSwitchedIds, enabled);
    }

    ourRestartNeededNotifier.showNotification();
  }

  private void showAutoSwitchNotification(@NotNull Collection<IdeaPluginDescriptor> autoSwitchedPlugins, boolean enabled) {
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
    Notification switchNotification = PLUGINS_AUTO_SWITCH_GROUP
      .createNotification(IdeBundle.message(contentKey, pluginString, dependenciesString), NotificationType.INFORMATION)
      .setTitle(IdeBundle.message(titleKey))
      .addAction(new UndoPluginsSwitchAction(autoSwitchedPlugins, enabled));

    PluginManager.getInstance().addDisablePluginListener(new Runnable() {
      @Override
      public void run() {
        Stream<PluginId> ids = autoSwitchedPlugins.stream().map(descriptor -> descriptor.getPluginId());
        boolean notificationValid = enabled ? ids.noneMatch(PluginManagerCore::isDisabled) : ids.allMatch(PluginManagerCore::isDisabled);
        if (!notificationValid) {
          switchNotification.expire();
        }

        Balloon balloon = switchNotification.getBalloon();
        if (balloon == null || balloon.isDisposed()) {
          ApplicationManager.getApplication().invokeLater(() -> PluginManager.getInstance().removeDisablePluginListener(this));
        }
      }
    });
    Notifications.Bus.notify(switchNotification);
  }

  @NotNull
  private static Collection<IdeaPluginDescriptor> getPluginsIdsToEnable(@NotNull IdeaPluginDescriptor rootDescriptor) {
    Set<IdeaPluginDescriptor> result = new HashSet<>();
    result.add(rootDescriptor);

    PluginManagerCore.processAllDependencies(rootDescriptor, false, descriptor -> {
      if (descriptor.getPluginId() == PluginManagerCore.CORE_ID) {
        return FileVisitResult.SKIP_SUBTREE;
      }

      if (!descriptor.isEnabled()) {
        // if descriptor was already added, no need to process it's dependencies again
        return result.add(descriptor) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
      }
      else {
        // if descriptor is already enabled, no need to process it's dependencies
        return FileVisitResult.SKIP_SUBTREE;
      }
    });
    return result;
  }

  @NotNull
  private static Collection<IdeaPluginDescriptor> getPluginsIdsToDisable(@NotNull IdeaPluginDescriptor rootDescriptor) {
    Set<IdeaPluginDescriptor> result = new HashSet<>();
    result.add(rootDescriptor);

    PluginManagerCore.processAllDependencies(rootDescriptor, false, descriptor -> {
      if (descriptor.getPluginId() == PluginManagerCore.CORE_ID) {
        return FileVisitResult.SKIP_SUBTREE;
      }

      if (descriptor.isEnabled()) {
        // if descriptor was already added, no need to process it's dependencies again
        return result.add(descriptor) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
      }
      else {
        // if descriptor is already disabled, no need to process it's dependencies
        return FileVisitResult.SKIP_SUBTREE;
      }
    });
    return result;
  }

  private static final class UndoPluginsSwitchAction extends NotificationAction {
    private final Collection<IdeaPluginDescriptor> myDescriptors;
    private final boolean myEnabled;

    UndoPluginsSwitchAction(@NotNull Collection<IdeaPluginDescriptor> descriptors, boolean enabled) {
      super(IdeBundle.message("plugins.auto.switch.action.name"));

      myDescriptors = descriptors;
      myEnabled = enabled;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
      boolean enabled = !myEnabled;
      PluginManager.getInstance().enablePlugins(myDescriptors, enabled);
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

      Notification next = PLUGINS_LIST_CHANGED_GROUP
        .createNotification(
          IdeBundle.message("plugins.changed.notification.content", ApplicationNamesInfo.getInstance().getFullProductName()),
          NotificationType.INFORMATION)
        .setTitle(IdeBundle.message("plugins.changed.notification.title"));

      if (prevNotification.compareAndSet(prev, next)) {
        Notifications.Bus.notify(next);
      }
    }
  }
}