package com.intellij.notification.impl;

import com.intellij.notification.NotificationType;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.*;

/**
 * @author spleaner
 */
public class NotificationModel<T extends Notification> {

  private final List<T> myNotifications = ContainerUtil.createEmptyCOWList();
  private final List<NotificationModelListener<T>> myListeners = ContainerUtil.createEmptyCOWList();
  private final List<T> myArchive = ContainerUtil.createEmptyCOWList();

  public void addListener(@NotNull final NotificationModelListener<T> listener) {
    myListeners.add(listener);
  }

  public void removeListener(@NotNull final NotificationModelListener<T> listener) {
    myListeners.remove(listener);
  }

  public void add(@NotNull final T notification) {
    myNotifications.add(0, notification);
    for (NotificationModelListener<T> listener : myListeners) {
      listener.notificationsAdded(notification);
    }
  }

  public void archive() {
    if (myNotifications.size() > 0) {
      myArchive.addAll(myNotifications);

      final T[] tba = myNotifications.toArray((T[])Array.newInstance(myNotifications.get(0).getClass(), myNotifications.size()));
      myNotifications.clear();

      for (final NotificationModelListener<T> listener : myListeners) {
        listener.notificationsArchived(tba);
      }
    }
  }

  @Nullable
  public T remove(final int index, @NotNull NotNullFunction<T, Boolean> filter) {
    final LinkedList<T> filtered = filterNotifications(filter);
    if (filtered.size() > 0 && index >= 0 && index < filtered.size()) {
      final T notification = filtered.get(index);
      if (notification != null) {
        myNotifications.remove(notification);

        for (NotificationModelListener<T> listener : myListeners) {
          listener.notificationsRemoved(notification);
        }

        return notification;
      }
    }

    return null;
  }

  @Nullable
  public T remove(@NotNull final T notification) {
    if (myNotifications.contains(notification)) {
      myNotifications.remove(notification);

      for (NotificationModelListener<T> listener : myListeners) {
        listener.notificationsRemoved(notification);
      }
    }

    return notification;
  }

  public void remove(@NotNull final T... notifications) {
    final List<T> tbr = new ArrayList<T>();
    for (T notification : notifications) {
      if (myNotifications.contains(notification)) {
        tbr.add(notification);
        myNotifications.remove(notification);
      } else if (myArchive.contains(notification)) {
        tbr.add(notification);
        myArchive.remove(notification);
      }
    }

    if (tbr.size() > 0) {
      for (NotificationModelListener<T> listener : myListeners) {
        listener.notificationsRemoved(tbr.toArray((T[])Array.newInstance(tbr.get(0).getClass(), tbr.size())));
      }
    }
  }

  @Nullable
  public T get(final int index, @NotNull NotNullFunction<T, Boolean> filter) {
    final LinkedList<T> filtered = filterNotifications(filter);
    if (index >= 0 && filtered.size() > index) {
      return filtered.get(index);
    }

    return null;
  }

  private LinkedList<T> filterNotifications(@NotNull NotNullFunction<T, Boolean> filter) {
    final LinkedList<T> result = new LinkedList<T>();
    for (final T notification : myNotifications) {
      if (filter.fun(notification)) {
        result.add(notification);
      }
    }

    return result;
  }


  public int getCount(@NotNull NotNullFunction<T, Boolean> filter) {
    return filterNotifications(filter).size();
  }

  public boolean isEmpty(@NotNull NotNullFunction<T, Boolean> filter) {
    return getCount(filter) == 0;
  }

  @Nullable
  public T getFirst(@NotNull NotNullFunction<T, Boolean> filter) {
    final LinkedList<T> result = filterNotifications(filter);
    if (result.size() > 0) {
      return result.getFirst();
    }

    return null;
  }

  public void clear(@NotNull NotNullFunction<T, Boolean> filter) {
    final LinkedList<T> result = filterNotifications(filter);
    for (final T notification : result) {
      myNotifications.remove(notification);
    }

    final Collection<T> archive = getArchive(filter);
    for (final T notification : archive) {
      myArchive.remove(notification);
    }

    result.addAll(archive);

    if (!result.isEmpty()) {
      final T[] removed = result.toArray((T[])Array.newInstance(result.get(0).getClass(), result.size()));
      for (NotificationModelListener<T> listener : myListeners) {
        listener.notificationsRemoved(removed);
      }
    }
  }

  public List<T> getAll(@Nullable final String id, @NotNull NotNullFunction<T, Boolean> filter) {
    if (id == null) {
      return Collections.unmodifiableList(filterNotifications(filter));
    }
    else {
      final List<T> result = new ArrayList<T>();
      final LinkedList<T> filtered = filterNotifications(filter);
      for (T notification : filtered) {
        if (id.equals(notification.getId())) {
          result.add(notification);
        }
      }

      return result;
    }
  }


  public List<T> getByType(@Nullable final NotificationType type, @NotNull NotNullFunction<T, Boolean> filter) {
    if (type == null) {
      return Collections.unmodifiableList(filterNotifications(filter));
    }
    else {
      final List<T> result = new ArrayList<T>();
      final LinkedList<T> filtered = filterNotifications(filter);
      for (T notification : filtered) {
        if (type == notification.getType()) {
          result.add(notification);
        }
      }

      return result;
    }
  }

  public void invalidateAll(final String id, @NotNull NotNullFunction<T, Boolean> filter) {
    if (id != null) {
      final List<T> all = getAll(id, filter);
      if (all.size() > 0) {
        T[] a = (T[])Array.newInstance(all.get(0).getClass(), all.size());
        remove(all.toArray(a));
      }
    }
  }

  public Collection<T> getArchive(@NotNull NotNullFunction<T, Boolean> filter) {
    final LinkedList<T> result = new LinkedList<T>();
    for (final T notification : myArchive) {
      if (filter.fun(notification)) {
        result.add(notification);
      }
    }

    return Collections.unmodifiableList(result);
  }
}
