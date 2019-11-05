// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.popup.Balloon;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Konstantin Bulenkov
 */
final class PluginBooleanOptionDescriptor extends BooleanOptionDescription {
  private static final Logger LOG = Logger.getInstance(PluginBooleanOptionDescriptor.class);

  private static final NotificationGroup PLUGINS_LIST_CHANGED_GROUP =
    new NotificationGroup("Plugins updates", NotificationDisplayType.STICKY_BALLOON, false);
  private static final NotificationGroup PLUGINS_AUTO_SWITCH_GROUP =
    new NotificationGroup("Plugins AutoSwitch", NotificationDisplayType.BALLOON, false);

  private static final Notifier ourRestartNeededNotifier = new Notifier();

  private final PluginId myId;

  PluginBooleanOptionDescriptor(@NotNull IdeaPluginDescriptor descriptor) {
    super(descriptor.getName(), PluginManagerConfigurable.ID);

    myId = descriptor.getPluginId();
  }

  @Override
  public boolean isOptionEnabled() {
    return optionalDescriptor(myId).map(IdeaPluginDescriptor::isEnabled).orElse(false);
  }

  @Override
  public void setOptionState(boolean enabled) {
    try {
      Collection<IdeaPluginDescriptor> autoSwitchedIds = enabled ? getPluginsIdsToEnable(myId) : getPluginsIdsToDisable(myId);
      switchPlugins(autoSwitchedIds, enabled);
      if (autoSwitchedIds.size() > 1) {
        showAutoSwitchNotification(autoSwitchedIds, enabled);
      }

      ourRestartNeededNotifier.showNotification();
    }
    catch (IOException e) {
      LOG.error("Cannot save plugins state");
    }
  }

  private void showAutoSwitchNotification(@NotNull Collection<IdeaPluginDescriptor> autoSwitchedPlugins, boolean enabled) {
    String pluginString = idToName(myId);
    String dependenciesString = autoSwitchedPlugins.stream()
      .map(descriptor -> '"' + descriptor.getName() + '"')
      .collect(Collectors.joining(", "));

    String titleKey = enabled ? "plugins.auto.enabled.notification.title" : "plugins.auto.disabled.notification.title";
    String contentKey = enabled ? "plugins.auto.enabled.notification.content" : "plugins.auto.disabled.notification.content";
    Notification switchNotification = PLUGINS_AUTO_SWITCH_GROUP
      .createNotification(IdeBundle.message(contentKey, pluginString, dependenciesString), NotificationType.INFORMATION)
      .setTitle(IdeBundle.message(titleKey))
      .addAction(new UndoPluginsSwitchAction(autoSwitchedPlugins, enabled));

    Runnable listener = new Runnable() {
      @Override
      public void run() {
        Stream<PluginId> ids = autoSwitchedPlugins.stream().map(descriptor -> descriptor.getPluginId());
        boolean notificationValid = enabled ? ids.noneMatch(PluginManagerCore::isDisabled) : ids.allMatch(PluginManagerCore::isDisabled);
        if (!notificationValid) {
          switchNotification.expire();
        }

        Balloon balloon = switchNotification.getBalloon();
        if (balloon == null || balloon.isDisposed()) {
          ApplicationManager.getApplication().invokeLater(() -> PluginManagerCore.removeDisablePluginListener(this));
        }
      }
    };

    PluginManagerCore.addDisablePluginListener(listener);
    Notifications.Bus.notify(switchNotification);
  }

  @NotNull
  private static String idToName(PluginId id) {
    return '"' + optionalDescriptor(id).map(IdeaPluginDescriptor::getName).orElse(id.getIdString()) + '"';
  }

  private static void switchPlugins(@NotNull Collection<IdeaPluginDescriptor> descriptors, boolean enabled) throws IOException {
    Collection<PluginId> disabledPlugins = new LinkedHashSet<>(PluginManagerCore.disabledPlugins());
    for (IdeaPluginDescriptor descriptor : descriptors) {
      if (enabled) {
        disabledPlugins.remove(descriptor.getPluginId());
      }
      else {
        disabledPlugins.add(descriptor.getPluginId());
      }
    }
    PluginManagerCore.saveDisabledPlugins(disabledPlugins, false);

    for (IdeaPluginDescriptor descriptor : descriptors) {
      descriptor.setEnabled(enabled);
    }
  }

  private static Optional<IdeaPluginDescriptor> optionalDescriptor(PluginId id) {
    return Optional.ofNullable(PluginManagerCore.getPlugin(id));
  }

  @NotNull
  private static Collection<IdeaPluginDescriptor> getPluginsIdsToEnable(@NotNull PluginId id) {
    IdeaPluginDescriptor rootDescriptor = PluginManagerCore.getPlugin(id);
    if (rootDescriptor == null) {
      return Collections.emptyList();
    }

    Set<IdeaPluginDescriptor> result = new HashSet<>();
    result.add(rootDescriptor);

    PluginManagerCore.processAllDependencies(rootDescriptor, false, descriptor -> {
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
  private static Collection<IdeaPluginDescriptor> getPluginsIdsToDisable(@NotNull PluginId id) {
    IdeaPluginDescriptor rootDescriptor = PluginManagerCore.getPlugin(id);
    if (rootDescriptor == null) {
      return Collections.emptyList();
    }

    Set<IdeaPluginDescriptor> result = new HashSet<>();
    result.add(rootDescriptor);

    PluginManagerCore.processAllDependencies(rootDescriptor, false, descriptor -> {
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
      try {
        switchPlugins(myDescriptors, !myEnabled);
        notification.expire();
        ourRestartNeededNotifier.showNotification();
      }
      catch (IOException exception) {
        LOG.error("Cannot save plugins state");
      }
    }
  }

  private static class Notifier {
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