package org.jetbrains.debugger.memory.tracking;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

class IdentityBasedTracking extends InstanceTrackingStrategy {
  private long myLastId = -1;
  private int myCount = 0;
  IdentityBasedTracking(@NotNull List<ObjectReference> initialInstances) {
    myLastId = getMaxId(initialInstances);
  }

  @NotNull
  @Override
  protected List<ObjectReference> updateImpl(@NotNull SuspendContextImpl suspendContext,
                                             @NotNull List<ObjectReference> references) {
    List<ObjectReference> result = references.stream()
        .filter(reference -> reference.uniqueID() > myLastId)
        .collect(Collectors.toList());
    myLastId = Math.max(myLastId, getMaxId(result));
    myCount = result.size();
    return result;
  }

  private long getMaxId(@NotNull List<ObjectReference> references) {
    return references.stream().map(ObjectReference::uniqueID).max(Long::compare).orElse(-1L);
  }

  @Override
  public int getCount() {
    return myCount;
  }
}
