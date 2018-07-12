/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.popup.Balloon;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
    super(PluginManager.getPlugin(id).getName(), PluginManagerConfigurable.ID);
    myId = id;
  }

  @Override
  public boolean isOptionEnabled() {
    //noinspection ConstantConditions
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

  private void showAutoSwitchNotification(Collection<PluginId> autoSwitchedIds, boolean enabled) {
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
        List<String> disabledPlugins = PluginManagerCore.getDisabledPlugins();
        List<String> ids = switchedPlugins.stream()
                                          .map(PluginId::getIdString)
                                          .collect(Collectors.toList());
        boolean notificationValid = enabled
                                    ? ids.stream().noneMatch(disabledPlugins::contains)
                                    : disabledPlugins.containsAll(ids);
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

  private static void switchPlugins(Collection<PluginId> ids, boolean enabled) throws IOException {
    Collection<String> disabledPlugins = new HashSet<>(PluginManagerCore.getDisabledPlugins());
    if (enabled) {
      ids.forEach(id -> disabledPlugins.remove(id.getIdString()));
    } else {
      ids.forEach(id -> disabledPlugins.add(id.getIdString()));
    }
    PluginManagerCore.saveDisabledPlugins(disabledPlugins, false);

    ids.forEach(id -> optionalDescriptor(id).ifPresent(descriptor -> descriptor.setEnabled(enabled)));
  }

  private static Optional<IdeaPluginDescriptor> optionalDescriptor(PluginId id) {
    return Optional.ofNullable(PluginManager.getPlugin(id));
  }

  private static Collection<PluginId> getPluginsIdsToEnable(PluginId id) {
    Optional<IdeaPluginDescriptor> maybeDescriptor = optionalDescriptor(id);
    if (!maybeDescriptor.isPresent()) {
      return Collections.emptyList();
    }

    Collection<PluginId> res = new HashSet<>();
    IdeaPluginDescriptor descriptor = maybeDescriptor.get();
    PluginManagerCore.checkDependants(descriptor, PluginManager::getPlugin, pluginId -> {
      boolean enabled = optionalDescriptor(pluginId).map(IdeaPluginDescriptor::isEnabled).orElse(true);
      if (!enabled) {
        res.add(pluginId);
      }
      return true;
    });
    return res;
  }

  private static Collection<PluginId> getPluginsIdsToDisable(PluginId id) {
    Collection<PluginId> res = new HashSet<>();
    Arrays.stream(PluginManagerCore.getPlugins())
          .filter(IdeaPluginDescriptor::isEnabled)
          .forEach(descriptor -> PluginManagerCore.checkDependants(descriptor, PluginManager::getPlugin, pluginId -> {
            if (pluginId.equals(id)) {
              res.add(descriptor.getPluginId());
              return false;
            }

            return true;
          }));
    return res;
  }

  private static class UndoPluginsSwitchAction extends NotificationAction {

    private final Collection<PluginId> myIds;
    private final boolean myEnabled;

    public UndoPluginsSwitchAction(Collection<PluginId> ids, boolean enabled) {
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
        .createNotification(IdeBundle.message("plugins.changed.notification.content"), NotificationType.INFORMATION)
        .setTitle(IdeBundle.message("plugins.changed.notification.title"));

      if (prevNotification.compareAndSet(prev, next)) {
        Notifications.Bus.notify(next);
      }
    }
  }
}
