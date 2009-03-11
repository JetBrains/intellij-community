package com.intellij.notification.impl;

import com.intellij.notification.impl.NotificationImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author spleaner
 */
public class NotificationModel {

  private LinkedList<NotificationImpl> myNotifications = new LinkedList<NotificationImpl>();
  private List<NotificationModelListener> myListeners = new CopyOnWriteArrayList<NotificationModelListener>();

  public void addListener(@NotNull final NotificationModelListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(@NotNull final NotificationModelListener listener) {
    myListeners.remove(listener);
  }

  public void add(@NotNull final NotificationImpl notification) {
    myNotifications.addFirst(notification);
    for (NotificationModelListener listener : myListeners) {
      listener.notificationsAdded(notification);
    }
  }

  @Nullable
  public NotificationImpl remove(final int index) {
    if (getCount() > 0 && index >= 0 && index < getCount()) {
      final NotificationImpl notification = myNotifications.get(index);
      if (notification != null) {
        myNotifications.remove(index);

        for (NotificationModelListener listener : myListeners) {
          listener.notificationsRemoved(notification);
        }

        return notification;
      }
    }

    return null;
  }

  @Nullable
  public NotificationImpl remove(@NotNull final NotificationImpl notification) {
    if (myNotifications.contains(notification)) {
      myNotifications.remove(notification);

      for (NotificationModelListener listener : myListeners) {
        listener.notificationsRemoved(notification);
      }
    }

    return notification;
  }

  public void remove(@NotNull final NotificationImpl... notifications) {
    final List<NotificationImpl> tbr = new ArrayList<NotificationImpl>();
    for (NotificationImpl notification : notifications) {
      if (myNotifications.contains(notification)) {
        tbr.add(notification);
        myNotifications.remove(notification);
      }
    }

    if (tbr.size() > 0) {
      for (NotificationModelListener listener : myListeners) {
        listener.notificationsRemoved(tbr.toArray(new NotificationImpl[tbr.size()]));
      }
    }
  }

  @Nullable
  public NotificationImpl get(final int index) {
    if (index >= 0 && myNotifications.size() > index) {
      return myNotifications.get(index);
    }

    return null;
  }

  public int getCount() {
    return myNotifications.size();
  }

  @Nullable
  public NotificationImpl getFirst() {
    if (myNotifications.size() > 0) {
      return myNotifications.getFirst();
    }

    return null;
  }

  public void clear() {
    if (getCount() > 0) {
      final NotificationImpl[] removed = myNotifications.toArray(new NotificationImpl[myNotifications.size()]);
      myNotifications.clear();

      for (NotificationModelListener listener : myListeners) {
        listener.notificationsRemoved(removed);
      }
    }
  }

  public List<NotificationImpl> getAll(final String componentName) {
    if (componentName == null) {
      return Collections.unmodifiableList(myNotifications);
    } else {
      final List<NotificationImpl> result = new ArrayList<NotificationImpl>();

      for (NotificationImpl notification : myNotifications) {
        if (componentName.equals(notification.getComponentName())) {
          result.add(notification);
        }
      }

      return result;
    }
  }
}
