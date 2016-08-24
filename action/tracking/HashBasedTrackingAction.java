package org.jetbrains.debugger.memory.action.tracking;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.memory.component.InstancesTracker.TrackingType;

public class HashBasedTrackingAction extends InstancesTrackingActionBase {
  @NotNull
  @Override
  protected TrackingType getType() {
    return TrackingType.HASH;
  }
}

