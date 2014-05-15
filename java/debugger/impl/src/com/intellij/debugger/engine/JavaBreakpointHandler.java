/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    return BreakpointManager.getJavaBreakpoint(xBreakpoint);
  }

  @Override
  public void registerBreakpoint(@NotNull XBreakpoint breakpoint) {
    final Breakpoint javaBreakpoint = createJavaBreakpoint(breakpoint);
    if (javaBreakpoint != null) {
      BreakpointManager.addBreakpointInt(javaBreakpoint);
      myProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
        @Override
        protected void action() throws Exception {
          javaBreakpoint.createRequest(myProcess);
        }
      });
    }
  }

  @Override
  public void unregisterBreakpoint(@NotNull final XBreakpoint breakpoint, boolean temporary) {
    final Breakpoint javaBreakpoint = BreakpointManager.getJavaBreakpoint(breakpoint);
    if (javaBreakpoint != null) {
      myProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
        @Override
        protected void action() throws Exception {
          javaBreakpoint.delete();
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
