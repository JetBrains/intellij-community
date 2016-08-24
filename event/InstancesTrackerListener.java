package org.jetbrains.debugger.memory.event;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.memory.component.InstancesTracker;

import java.util.EventListener;

public interface InstancesTrackerListener extends EventListener{
  default void classChanged(@NotNull String name, @NotNull InstancesTracker.TrackingType type) {
  }

  default void classRemoved(@NotNull String name) {
  }
}
