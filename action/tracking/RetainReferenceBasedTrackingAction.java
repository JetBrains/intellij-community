package org.jetbrains.debugger.memory.action.tracking;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.memory.component.InstancesTracker;

public class RetainReferenceBasedTrackingAction extends InstancesTrackingActionBase {
  @NotNull
  @Override
  protected InstancesTracker.TrackingType getType() {
    return InstancesTracker.TrackingType.RETAIN;
  }
}
