// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.sun.jdi.Method;
import one.util.streamex.StreamEx;
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties;

public interface MethodBreakpointBase extends FilteredRequestor {
  String METHOD_ENTRY_KEY = "METHOD_ENTRY_KEY";

  XBreakpoint<JavaMethodBreakpointProperties> getXBreakpoint();

  boolean isWatchEntry();

  boolean isWatchExit();

  StreamEx<Method> matchingMethods(StreamEx<Method> methods, DebugProcessImpl debugProcess);

  void disableEmulation();

  static boolean canBeWatchExitEmulated(DebugProcessImpl debugProcess) {
    VirtualMachineProxyImpl virtualMachineProxy = debugProcess.getVirtualMachineProxy();
    return virtualMachineProxy.canGetBytecodes() && virtualMachineProxy.canGetConstantPool();
  }

  static void disableEmulation(Breakpoint<JavaMethodBreakpointProperties> breakpoint) {
    ApplicationManager.getApplication().invokeLater(() -> {
      breakpoint.getProperties().EMULATED = false;
      breakpoint.fireBreakpointChanged();
    });
  }
}
