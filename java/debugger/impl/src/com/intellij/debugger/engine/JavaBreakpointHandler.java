// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.debugger.ui.breakpoints.*;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaBreakpointHandler extends XBreakpointHandler {
  protected final DebugProcessImpl myProcess;

  public JavaBreakpointHandler(@NotNull Class<? extends XBreakpointType<?, ?>> breakpointTypeClass, DebugProcessImpl process) {
    super(breakpointTypeClass);
    myProcess = process;
  }

  protected @Nullable Breakpoint createJavaBreakpoint(@NotNull XBreakpoint xBreakpoint) {
    return null;
  }

  @Override
  public void registerBreakpoint(@NotNull XBreakpoint breakpoint) {
    Breakpoint javaBreakpoint = BreakpointManager.getJavaBreakpoint(breakpoint);
    if (javaBreakpoint == null) {
      javaBreakpoint = createJavaBreakpoint(breakpoint);
      breakpoint.putUserData(Breakpoint.DATA_KEY, javaBreakpoint);
    }
    if (javaBreakpoint != null) {
      final Breakpoint bpt = javaBreakpoint;
      BreakpointManager.addBreakpoint(bpt);
      // use schedule not to block initBreakpoints
      myProcess.getManagerThread().schedule(PrioritizedTask.Priority.HIGH, () -> bpt.createRequest(myProcess));
    }
  }

  @Override
  public void unregisterBreakpoint(final @NotNull XBreakpoint breakpoint, boolean temporary) {
    final Breakpoint javaBreakpoint = BreakpointManager.getJavaBreakpoint(breakpoint);
    if (javaBreakpoint != null) {
      // use schedule not to block initBreakpoints
      myProcess.getManagerThread().schedule(PrioritizedTask.Priority.HIGH,
                                            () -> myProcess.getRequestsManager().deleteRequest(javaBreakpoint));
    }
  }

  public static class JavaLineBreakpointHandler extends JavaBreakpointHandler {
    public JavaLineBreakpointHandler(DebugProcessImpl process) {
      super(JavaLineBreakpointType.class, process);
    }
  }

  public static class JavaExceptionBreakpointHandler extends JavaBreakpointHandler {
    public JavaExceptionBreakpointHandler(DebugProcessImpl process) {
      super(JavaExceptionBreakpointType.class, process);
    }
  }

  public static class JavaMethodBreakpointHandler extends JavaBreakpointHandler {
    public JavaMethodBreakpointHandler(DebugProcessImpl process) {
      super(JavaMethodBreakpointType.class, process);
    }
  }

  public static class JavaWildcardBreakpointHandler extends JavaBreakpointHandler {
    public JavaWildcardBreakpointHandler(DebugProcessImpl process) {
      super(JavaWildcardMethodBreakpointType.class, process);
    }
  }

  public static class JavaFieldBreakpointHandler extends JavaBreakpointHandler {
    public JavaFieldBreakpointHandler(DebugProcessImpl process) {
      super(JavaFieldBreakpointType.class, process);
    }
  }

  public static class JavaCollectionBreakpointHandler extends JavaBreakpointHandler {
    public JavaCollectionBreakpointHandler(DebugProcessImpl process) {
      super(JavaCollectionBreakpointType.class, process);
    }
  }
}
