// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.util.ObjectUtils;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ServiceEventListener {
  Topic<ServiceEventListener> TOPIC =
    Topic.create("services topic", ServiceEventListener.class, Topic.BroadcastDirection.TO_CHILDREN);

  /**
   * @deprecated has no effect since 2021.2, shall be removed in 2022.1
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  Object POLLING_RESET_TARGET = ObjectUtils.sentinel("pollingResetTarget");

  void handle(@NotNull ServiceEvent event);

  final class ServiceEvent {
    public final EventType type;
    public final Object target;
    public final Class<?> contributorClass;

    public final Object parent;

    private ServiceEvent(@NotNull EventType type,
                         @NotNull Object target,
                         @NotNull Class<?> contributorClass) {
      this(type, target, contributorClass, null);
    }

    private ServiceEvent(@NotNull EventType type,
                         @NotNull Object target,
                         @NotNull Class<?> contributorClass,
                         @Nullable Object parent) {
      this.type = type;
      this.target = target;
      this.contributorClass = contributorClass;
      this.parent = parent;
    }

    @Override
    public String toString() {
      return type + ": " + target.toString() + "; from contributor: " + contributorClass +
             (parent == null ? "" : "; parent: " + parent);
    }

    public static ServiceEvent createEvent(@NotNull EventType type,
                                           @NotNull Object target,
                                           @NotNull Class<?> rootContributorClass) {
      return new ServiceEvent(type, target, rootContributorClass);
    }

    public static ServiceEvent createResetEvent(@NotNull Class<?> rootContributorClass) {
      return new ServiceEvent(EventType.RESET, rootContributorClass, rootContributorClass);
    }

    public static ServiceEvent createSyncResetEvent(@NotNull Class<?> rootContributorClass) {
      return new ServiceEvent(EventType.SYNC_RESET, rootContributorClass, rootContributorClass);
    }

    public static ServiceEvent createServiceAddedEvent(@NotNull Object target,
                                                       @NotNull Class<?> contributorClass,
                                                       @Nullable Object parent) {
      return new ServiceEvent(EventType.SERVICE_ADDED, target, contributorClass, parent);
    }
  }

  enum EventType {
    RESET, SYNC_RESET,
    SERVICE_ADDED, SERVICE_REMOVED, SERVICE_CHANGED, SERVICE_STRUCTURE_CHANGED, SERVICE_GROUP_CHANGED,
    GROUP_CHANGED
  }
}
