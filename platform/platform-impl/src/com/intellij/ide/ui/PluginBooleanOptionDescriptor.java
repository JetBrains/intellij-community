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
import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Konstantin Bulenkov
 */
public class PluginBooleanOptionDescriptor extends BooleanOptionDescription {
  private static final Logger LOG = Logger.getInstance(PluginBooleanOptionDescriptor.class);

  private static final NotificationGroup PLUGINS_LIST_CHANGED_GROUP =
    new NotificationGroup("Plugins updates", NotificationDisplayType.STICKY_BALLOON, false);
  private static final NotificationGroup PLUGINS_AUTO_SWITCH_GROUP =
    new NotificationGroup("Plugins AutoSwitch", NotificationDisplayType.BALLOON, false);

  private static final Notifier ourRestartNeededNotifier = new Notifier();

  private final PluginId myId;

  public PluginBooleanOptionDescriptor(PluginId id) {
    //noinspection ConstantConditions
    super(PluginManagerCore.getPlugin(id).getName(), PluginManagerConfigurable.ID);
    myId = id;
  }

  @Override
  public boolean isOptionEnabled() {
    return optionalDescriptor(myId).map(IdeaPluginDescriptor::isEnabled).orElse(false);
  }

  @Override
  public void setOptionState(boolean enabled) {
    try {
      Collection<PluginId> autoSwitchedIds = enabled ? getPluginsIdsToEnable(myId) : getPluginsIdsToDisable(myId);
      Collection<PluginId> switchedPlugins = new ArrayList<>(autoSwitchedIds);
      switchedPlugins.add(myId);
      switchPlugins(switchedPlugins, enabled);

      if (!autoSwitchedIds.isEmpty()) {
        showAutoSwitchNotification(autoSwitchedIds, enabled);
      }

      ourRestartNeededNotifier.showNotification();
    }
    catch (IOException e) {
      LOG.error("Cannot save plugins state");
    }
  }

  private void showAutoSwitchNotification(Collection<? extends PluginId> autoSwitchedIds, boolean enabled) {
    Collection<PluginId> switchedPlugins = new ArrayList<>(autoSwitchedIds);
    switchedPlugins.add(myId);

    String pluginString = idToName(myId);
    String dependenciesString = autoSwitchedIds.stream()
                                               .map(id -> idToName(id))
                                               .collect(Collectors.joining(", "));

    String titleKey = enabled ? "plugins.auto.enabled.notification.title" : "plugins.auto.disabled.notification.title";
    String contentKey = enabled ? "plugins.auto.enabled.notification.content" : "plugins.auto.disabled.notification.content";
    Notification switchNotification = PLUGINS_AUTO_SWITCH_GROUP
      .createNotification(IdeBundle.message(contentKey, pluginString, dependenciesString), NotificationType.INFORMATION)
      .setTitle(IdeBundle.message(titleKey))
      .addAction(new UndoPluginsSwitchAction(switchedPlugins, enabled));

    Runnable listener = new Runnable() {
      @Override
      public void run() {
        Stream<String> ids = switchedPlugins.stream().map(PluginId::getIdString);
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

  private static void switchPlugins(Collection<? extends PluginId> ids, boolean enabled) throws IOException {
    Collection<String> disabledPlugins = new LinkedHashSet<>(PluginManagerCore.disabledPlugins());
    for (PluginId id : ids) {
      if (enabled) disabledPlugins.remove(id.getIdString());
              else disabledPlugins.add(id.getIdString());
    }
    PluginManagerCore.saveDisabledPlugins(disabledPlugins, false);

    ids.forEach(id -> optionalDescriptor(id).ifPresent(descriptor -> descriptor.setEnabled(enabled)));
  }

  private static Optional<IdeaPluginDescriptor> optionalDescriptor(PluginId id) {
    return Optional.ofNullable(PluginManagerCore.getPlugin(id));
  }

  private static Collection<PluginId> getPluginsIdsToEnable(PluginId id) {
    Optional<IdeaPluginDescriptor> maybeDescriptor = optionalDescriptor(id);
    if (!maybeDescriptor.isPresent()) {
      return Collections.emptyList();
    }

    Collection<PluginId> res = new HashSet<>();
    IdeaPluginDescriptor descriptor = maybeDescriptor.get();
    PluginId pluginId = descriptor.getPluginId();
    for (PluginId depId : PluginManagerCore.pluginIdTraverser().withRoot(pluginId)) {
      if (depId.equals(pluginId)) continue;
      boolean enabled = optionalDescriptor(depId).map(IdeaPluginDescriptor::isEnabled).orElse(true);
      if (!enabled) {
        res.add(depId);
      }
    }
    return res;
  }

  private static Collection<PluginId> getPluginsIdsToDisable(PluginId id) {
    Collection<PluginId> res = new LinkedHashSet<>();
    JBTreeTraverser<PluginId> traverser = PluginManagerCore.pluginIdTraverser();
    for (IdeaPluginDescriptor plugin : PluginManagerCore.getPlugins()) {
      if (!plugin.isEnabled()) continue;
      if (traverser.withRoot(plugin.getPluginId()).unique().traverse().contains(id)) {
        res.add(plugin.getPluginId());
      }
    }
    return res;
  }

  private static class UndoPluginsSwitchAction extends NotificationAction {

    private final Collection<? extends PluginId> myIds;
    private final boolean myEnabled;

    UndoPluginsSwitchAction(Collection<? extends PluginId> ids, boolean enabled) {
      super(IdeBundle.message("plugins.auto.switch.action.name"));
      myIds = ids;
      myEnabled = enabled;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
      try {
        switchPlugins(myIds, !myEnabled);
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