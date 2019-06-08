// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface ServiceEventListener {
  Topic<ServiceEventListener> TOPIC =
    Topic.create("services topic", ServiceEventListener.class, Topic.BroadcastDirection.TO_CHILDREN);

  void handle(@NotNull ServiceEvent event);

  final class ServiceEvent {
    public final EventType type;
    public final Object target;
    public final Class<?> contributorClass;

    public ServiceEvent(@NotNull Class<?> contributorClass) {
      this(EventType.RESET, contributorClass, contributorClass);
    }

    public ServiceEvent(@NotNull EventType type,
                        @NotNull Object target,
                        @NotNull Class<?> contributorClass) {
      this.type = type;
      this.target = target;
      this.contributorClass = contributorClass;
    }
  }

  enum EventType {
    RESET,
    SERVICE_ADDED, SERVICE_REMOVED, SERVICE_CHANGED, SERVICE_STRUCTURE_CHANGED,
    GROUP_CHANGED
  }
}
