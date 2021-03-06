// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.notification;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 */
public final class MessageCounter {
  private final Map<ProjectSystemId, Map<String/* group */, Map<NotificationSource, Object2IntOpenHashMap<NotificationCategory>>>>
    map = new HashMap<>();

  public synchronized void increment(@NotNull String groupName,
                                     @NotNull NotificationSource source,
                                     @NotNull NotificationCategory category,
                                     @NotNull ProjectSystemId projectSystemId) {
    Object2IntMap<NotificationCategory> counter = map.computeIfAbsent(projectSystemId, __ -> new HashMap<>())
      .computeIfAbsent(groupName, __ -> new HashMap<>())
      .computeIfAbsent(source, __ -> new Object2IntOpenHashMap<>());
    counter.mergeInt(category, 1, Math::addExact);
  }

  public synchronized void remove(@Nullable String groupName,
                                  @NotNull NotificationSource notificationSource,
                                  @NotNull ProjectSystemId projectSystemId) {
    Map<String, Map<NotificationSource, Object2IntOpenHashMap<NotificationCategory>>> groupMap =
      map.computeIfAbsent(projectSystemId, __ -> new HashMap<>());
    if (groupName == null) {
      for (Map<NotificationSource, Object2IntOpenHashMap<NotificationCategory>> sourceMap : groupMap.values()) {
        sourceMap.remove(notificationSource);
      }
    }
    else {
      Object2IntMap<NotificationCategory> counter = groupMap.computeIfAbsent(groupName, __ -> new HashMap<>())
        .computeIfAbsent(notificationSource, __ -> new Object2IntOpenHashMap<>());
      counter.clear();
    }
  }

  public synchronized int getCount(@Nullable final String groupName,
                                   @NotNull final NotificationSource notificationSource,
                                   @Nullable final NotificationCategory notificationCategory,
                                   @NotNull final ProjectSystemId projectSystemId) {
    int count = 0;
    Map<String, Map<NotificationSource, Object2IntOpenHashMap<NotificationCategory>>> value = map.get(projectSystemId);
    Map<String, Map<NotificationSource, Object2IntOpenHashMap<NotificationCategory>>> groupMap = value == null ? Collections.emptyMap() : value;
    for (Map.Entry<String, Map<NotificationSource, Object2IntOpenHashMap<NotificationCategory>>> entry : groupMap.entrySet()) {
      if (groupName == null || groupName.equals(entry.getKey())) {
        Object2IntMap<NotificationCategory> counter = entry.getValue().get(notificationSource);
        if (counter == null) {
          continue;
        }
        if (notificationCategory == null) {
          for (IntIterator iterator = counter.values().iterator(); iterator.hasNext(); ) {
            count += iterator.nextInt();
          }
        }
        else {
          count += counter.getInt(notificationCategory);
        }
      }
    }

    return count;
  }

  @Override
  public String toString() {
    return "MessageCounter{" +
           "map=" + map +
           '}';
  }
}


