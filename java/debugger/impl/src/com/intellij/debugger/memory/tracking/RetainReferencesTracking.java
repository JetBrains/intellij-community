package org.jetbrains.debugger.memory.tracking;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class RetainReferencesTracking extends InstanceTrackingStrategy {
  private final IdentityBasedTracking myIdTracker;
  private List<ObjectReference> myHardRefs;
  RetainReferencesTracking(@NotNull List<ObjectReference> initialInstances) {
    myHardRefs = initialInstances;
    myIdTracker = new IdentityBasedTracking(initialInstances);
  }

  @NotNull
  @Override
  protected List<ObjectReference> updateImpl(@NotNull SuspendContextImpl suspendContext,
                                             @NotNull List<ObjectReference> references) {
    myIdTracker.update(suspendContext, references);
    myHardRefs = references;
    return myIdTracker.getNewInstances();
  }

  @Override
  public int getCount() {
    return myHardRefs.size();
  }
}
