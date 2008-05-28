package com.intellij.ui;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author mike
 */
@State(
  name = "SystemNotifications",
  storages = {
    @Storage(
      id="SystemNotifications",
      file="$APP_CONFIG$/other.xml"
    )}
)
public class SystemNotificationsImpl implements SystemNotifications, PersistentStateComponent<SystemNotificationsImpl.State> {
  private State myState = new State();
  private boolean myCannotInitializeNotifications = false;

  public void notify(@NotNull String notificationName, @NotNull String title, @NotNull String text) {
    if (myCannotInitializeNotifications || !SystemInfo.isMac) return;

    final GrowlNotifications nofications;
    try {
      nofications = GrowlNotifications.getNofications();
    }
    catch (Throwable e) {
      myCannotInitializeNotifications = true;
      return;
    }

    myState.NOTIFICATIONS.add(notificationName);
    nofications.notify(myState.NOTIFICATIONS, notificationName, title, text);
  }

  public State getState() {
    return myState;
  }

  public void loadState(final State state) {
    myState = state;
  }


  public static class State {
    public Set<String> NOTIFICATIONS = new HashSet<String>();
  }
}
