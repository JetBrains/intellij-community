package org.jetbrains.debugger.memory.action.tracking;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.memory.tracking.TrackingType;

public class TrackInstanceCreationAction extends InstancesTrackingActionBase{
  @NotNull
  @Override
  protected TrackingType getType() {
    return TrackingType.CREATION;
  }
}
