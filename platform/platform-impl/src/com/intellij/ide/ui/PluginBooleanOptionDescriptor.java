// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.popup.Balloon;
import org.jetbrains.annotations.NotNull;

import java.nio.file.FileVisitResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * @author Konstantin Bulenkov
 */
final class PluginBooleanOptionDescriptor extends NotABooleanOptionDescription implements BooleanOptionDescription.RequiresRebuild {
  private static final NotificationGroup PLUGINS_LIST_CHANGED_GROUP =
    new NotificationGroup("Plugins updates", NotificationDisplayType.STICKY_BALLOON, true, null, null, null, PluginManagerCore.CORE_ID);
  private static final NotificationGroup PLUGINS_AUTO_SWITCH_GROUP =
    new NotificationGroup("Plugins AutoSwitch", NotificationDisplayType.BALLOON, true, null, null, null, PluginManagerCore.CORE_ID);

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
    boolean enabledWithoutRestart = PluginEnabler.enablePlugins(
      null,
      new ArrayList<>(autoSwitchedIds),
      enabled
    );
    if (autoSwitchedIds.size() > 1) {
      showAutoSwitchNotification(autoSwitchedIds, enabled);
    }

    if (!enabledWithoutRestart) {
      ourRestartNeededNotifier.showNotification();
    }
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
        Stream<PluginId> ids = autoSwitchedPlugins.stream().map(PluginDescriptor::getPluginId);
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

      Notification next = PLUGINS_LIST_CHANGED_GROUP
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