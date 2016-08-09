package org.jetbrains.debugger.memory.component;

public final class MemoryViewManagerState {
  public boolean isShowWithInstancesOnly = true;
  public boolean isShowWithDiffOnly = false;

  MemoryViewManagerState() {
  }

  MemoryViewManagerState(MemoryViewManagerState other) {
    isShowWithInstancesOnly = other.isShowWithInstancesOnly;
    isShowWithDiffOnly = other.isShowWithDiffOnly;
  }
}
