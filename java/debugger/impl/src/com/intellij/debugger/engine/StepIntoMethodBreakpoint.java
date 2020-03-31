// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.ui.breakpoints.SteppingBreakpoint;
import com.intellij.openapi.project.Project;
import com.sun.jdi.event.LocatableEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StepIntoMethodBreakpoint extends SyntheticMethodBreakpoint implements SteppingBreakpoint {
  private RequestHint myHint;

  public StepIntoMethodBreakpoint(String className, String methodName, @Nullable String signature, Project project) {
    super(className, methodName, signature, project);
  }

  @Override
  public boolean isRestoreBreakpoints() {
    return true;
  }

  @Override
  public void setRequestHint(RequestHint hint) {
    myHint = hint;
  }

  @Override
  public boolean processLocatableEvent(@NotNull SuspendContextCommandImpl action, LocatableEvent event)
    throws EventProcessingException {
    boolean res = super.processLocatableEvent(action, event);
    SuspendContextImpl context = action.getSuspendContext();
    if (res && context != null) {
      context.getDebugProcess().resetIgnoreSteppingFilters(event.location(), myHint);
    }
    return res;
  }

  @Override
  public boolean track() {
    return false;
  }
}
