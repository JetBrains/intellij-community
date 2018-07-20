// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.ui.breakpoints.*;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author egor
 */
public class JavaBreakpointHandler extends XBreakpointHandler {
  protected final DebugProcessImpl myProcess;

  public JavaBreakpointHandler(@NotNull Class<? extends XBreakpointType<?, ?>> breakpointTypeClass, DebugProcessImpl process) {
    super(breakpointTypeClass);
    myProcess = process;
  }

  @Nullable
  protected Breakpoint createJavaBreakpoint(@NotNull XBreakpoint xBreakpoint) {
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
      myProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
        @Override
        protected void action() {
          bpt.createRequest(myProcess);
        }

        @Override
        public Priority getPriority() {
          return Priority.HIGH;
        }
      });
    }
  }

  @Override
  public void unregisterBreakpoint(@NotNull final XBreakpoint breakpoint, boolean temporary) {
    final Breakpoint javaBreakpoint = BreakpointManager.getJavaBreakpoint(breakpoint);
    if (javaBreakpoint != null) {
      // use schedule not to block initBreakpoints
      myProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
        @Override
        protected void action() {
          myProcess.getRequestsManager().deleteRequest(javaBreakpoint);
        }

        @Override
        public Priority getPriority() {
          return Priority.HIGH;
        }
      });
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
}
