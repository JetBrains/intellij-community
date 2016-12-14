package org.jetbrains.debugger.memory.utils;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import org.jetbrains.annotations.Nullable;

public class LowestPriorityCommand extends SuspendContextCommandImpl {
  protected LowestPriorityCommand(@Nullable SuspendContextImpl suspendContext) {
    super(suspendContext);
  }

  @Override
  protected void commandCancelled() {
  }

  @Override
  public Priority getPriority() {
    return Priority.LOWEST;
  }
}
