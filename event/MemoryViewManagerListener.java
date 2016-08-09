package org.jetbrains.debugger.memory.event;

import org.jetbrains.debugger.memory.component.MemoryViewManagerState;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

@FunctionalInterface
public interface MemoryViewManagerListener extends EventListener {
  void stateChanged(@NotNull MemoryViewManagerState state);
}
