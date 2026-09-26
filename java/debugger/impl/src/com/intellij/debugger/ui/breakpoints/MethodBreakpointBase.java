// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.requests.Requestor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.sun.jdi.Method;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.LocatableEvent;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties;

import java.util.Objects;

public interface MethodBreakpointBase extends FilteredRequestor {
  String METHOD_ENTRY_KEY = "METHOD_ENTRY_KEY";
  Key<Method> EXPECTED_METHOD = Key.create("EXPECTED_METHOD");

  XBreakpoint<JavaMethodBreakpointProperties> getXBreakpoint();

  boolean isEmulated();

  boolean isWatchEntry();

  boolean isWatchExit();

  StreamEx<Method> matchingMethods(StreamEx<Method> methods, DebugProcessImpl debugProcess);

  void disableEmulation();

  static boolean canBeWatchExitEmulated(VirtualMachine virtualMachine) {
    return virtualMachine.canGetBytecodes() && virtualMachine.canGetConstantPool();
  }

  static void disableEmulation(Breakpoint<JavaMethodBreakpointProperties> breakpoint) {
    ApplicationManager.getApplication().invokeLater(() -> {
      breakpoint.getProperties().EMULATED = false;
      breakpoint.fireBreakpointChanged();
    });
  }

  /**
   * A wrapper around the regular event processing that makes method return values
   * available at emulated method exit breakpoints.
   */
  static boolean processWithReturnValueCapture(@NotNull MethodBreakpointBase breakpoint,
                                               @NotNull SuspendContextCommandImpl action,
                                               LocatableEvent event,
                                               @NotNull Logger logger,
                                               @NotNull ThrowableComputable<Boolean, EventProcessingException> regularProcessing)
    throws EventProcessingException {

    // Return value capture for emulated method exit breakpoints is a two-step process:
    //
    // 1. On an emulated breakpoint hit, we create a transient non-emulated method exit
    //    breakpoint and resume the session.
    // 2. Immediately after, we hit the transient breakpoint, at which point we can access
    //    the return value from methods' exit event. We then delete the transient breakpoint.

    var requestManager = Objects.requireNonNull(action.getSuspendContext()).getDebugProcess().getRequestsManager();
    var eventRequest = Objects.requireNonNull(event).request();

    if (eventRequest.getProperty(RequestManagerImpl.TRANSIENT_REQUESTOR) instanceof Requestor transientRequestor) {
      // The event must match the exact invocation that hit the return opcode.
      // On a mismatch, keep the request for the intended exit.
      var expectedMethod = eventRequest.getProperty(EXPECTED_METHOD);
      if (!event.location().method().equals(expectedMethod)) {
        logger.error("Method exit event for " + event.location().method() + " does not match the expected method " + expectedMethod);
        return false;
      }

      requestManager.deleteRequest(transientRequestor);
      return regularProcessing.compute();
    }

    if (Registry.is("debugger.emulated.method.return.values") &&
        breakpoint.isEmulated() &&
        breakpoint.isWatchExit() &&
        eventRequest.getProperty(METHOD_ENTRY_KEY) instanceof Boolean entryKey &&
        !entryKey) {
      // The return opcode was hit. Defer processing to the matching method exit event, which carries the return value.
      var request = requestManager.createTransientMethodExitRequest(breakpoint);
      request.addThreadFilter(event.thread());
      request.addClassFilter(event.location().declaringType());
      request.putProperty(EXPECTED_METHOD, event.location().method());
      requestManager.enableRequest(request);
      return false; // Suspends later on the matching method exit event.
    }

    return regularProcessing.compute();
  }
}
