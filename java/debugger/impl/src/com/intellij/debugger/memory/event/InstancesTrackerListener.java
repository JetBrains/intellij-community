package org.jetbrains.debugger.memory.event;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.memory.tracking.TrackingType;

import java.util.EventListener;

public interface InstancesTrackerListener extends EventListener {
  default void classChanged(@NotNull String name, @NotNull TrackingType type) {
  }

  default void classRemoved(@NotNull String name) {
  }

  default void backgroundTrackingValueChanged(boolean newState) {
  }
}
