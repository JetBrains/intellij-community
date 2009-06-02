package com.intellij.notification.impl;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.messages.MessageBus;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author spleaner
 */
@State(name = "NotificationConfiguration",
       storages = {
         @Storage(id = "other", file = "$APP_CONFIG$/notifications.xml")
       })
public class NotificationsConfiguration implements ApplicationComponent, Notifications, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.notification.impl.NotificationsConfiguration");

  private Map<String, NotificationSettings> myIdToSettingsMap = new LinkedHashMap<String, NotificationSettings>();
  private MessageBus myMessageBus;

  public NotificationsConfiguration(@NotNull final MessageBus bus) {
    myMessageBus = bus;
  }

  public static NotificationsConfiguration getNotificationsConfiguration() {
    return ApplicationManager.getApplication().getComponent(NotificationsConfiguration.class);
  }

  public static NotificationSettings[] getAllSettings() {
    return getNotificationsConfiguration()._getAllSettings();
  }

  public static void remove(NotificationSettings[] toRemove) {
    getNotificationsConfiguration()._remove(toRemove);
  }

  public static void removeAll() {
    getNotificationsConfiguration()._removeAll();
  }

  private void _removeAll() {
    myIdToSettingsMap.clear();
  }

  private void _remove(NotificationSettings[] toRemove) {
    for (final NotificationSettings settings : toRemove) {
      myIdToSettingsMap.remove(settings.getComponentName());
    }
  }

  private NotificationSettings[] _getAllSettings() {
    final List<NotificationSettings> result = new ArrayList<NotificationSettings>(myIdToSettingsMap.values());

    Collections.sort(result, new Comparator<NotificationSettings>() {
      public int compare(NotificationSettings o1, NotificationSettings o2) {
        return o1.getComponentName().compareToIgnoreCase(o2.getComponentName());
      }
    });

    return result.toArray(new NotificationSettings[result.size()]);
  }

  @Nullable
  public static NotificationSettings getSettings(@NotNull final NotificationImpl notification) {
    final NotificationsConfiguration configuration = getNotificationsConfiguration();
    return configuration.myIdToSettingsMap.get(notification.getId());
  }

  @NotNull
  public String getComponentName() {
    return "NotificationsConfiguration";
  }

  public void initComponent() {
    myMessageBus.connect().subscribe(TOPIC, this);
  }

  public void disposeComponent() {
    myIdToSettingsMap.clear();
  }

  public void register(@NotNull final String id, @NotNull final NotificationDisplayType displayType, final boolean canDisable) {
    if (!myIdToSettingsMap.containsKey(id)) {
      myIdToSettingsMap.put(id, new NotificationSettings(id, displayType, canDisable));
    }
  }

  public boolean isRegistered(@NotNull final String id) {
    return myIdToSettingsMap.containsKey(id);
  }

  public void notify(@NotNull final String id,
                     @NotNull final String name,
                     @NotNull final String description,
                     @NotNull final NotificationType type,
                     @NotNull final NotificationListener handler) {
    // do nothing
  }

  public void notify(@NotNull final String id,
                     @NotNull final String name,
                     @NotNull final String description,
                     @NotNull final NotificationType type,
                     @NotNull final NotificationListener handler,
                     @Nullable final Icon icon) {
    // do nothing
  }

  public void invalidateAll(@NotNull final String id) {
    // do nothing
  }

  public Element getState() {
    @NonNls Element element = new Element("NotificationsConfiguration");
    for (NotificationSettings settings : myIdToSettingsMap.values()) {
      element.addContent(settings.save());
    }

    return element;
  }

  public void loadState(final Element state) {
    for (@NonNls Element child : (Iterable<? extends Element>)state.getChildren("notification")) {
      final NotificationSettings settings = NotificationSettings.load(child);
      final String id = settings.getComponentName();
      LOG.assertTrue(!myIdToSettingsMap.containsKey(id), String.format("Settings for '%s' already loaded!", id));

      myIdToSettingsMap.put(id, settings);
    }
  }
}
