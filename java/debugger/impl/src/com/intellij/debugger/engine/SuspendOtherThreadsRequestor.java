// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.breakpoints.FilteredRequestor;
import com.intellij.ui.classFilter.ClassFilter;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;

class SuspendOtherThreadsRequestor implements FilteredRequestor {
  private final @NotNull DebugProcessImpl myProcess;
  private final @NotNull SuspendContextImpl myThreadSuspendContext;

  SuspendOtherThreadsRequestor(@NotNull DebugProcessImpl process, @NotNull SuspendContextImpl threadSuspendContext) {
    myProcess = process;
    myThreadSuspendContext = threadSuspendContext;
  }

  static void enableRequest(DebugProcessImpl process, @NotNull SuspendContextImpl threadSuspendContext) {
    var requestor = new SuspendOtherThreadsRequestor(process, threadSuspendContext);
    var request = process.getRequestsManager().createMethodEntryRequest(requestor);

    request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
    request.setEnabled(true);
  }

  @Override
  public boolean processLocatableEvent(@NotNull SuspendContextCommandImpl action, LocatableEvent event) {
    myProcess.getRequestsManager().deleteRequest(this);

    SuspendContextImpl suspendContext = action.getSuspendContext();
    if (suspendContext == null) return false;

    // Need to 'replace' the myThreadSuspendContext (single-thread suspend context passed filtering) with this one.
    suspendContext.setAnotherThreadToFocus(myThreadSuspendContext.getThread());

    // Note, myThreadSuspendContext is resuming without SuspendManager#voteSuspend.
    // Look at the end of DebugProcessEvents#processLocatableEvent for more details.
    myProcess.getSuspendManager().voteResume(myThreadSuspendContext);
    return true;
  }

  @Override
  public String getSuspendPolicy() {
    return DebuggerSettings.SUSPEND_ALL;
  }

  @Override
  public boolean isInstanceFiltersEnabled() {
    return false;
  }

  @Override
  public InstanceFilter[] getInstanceFilters() {
    return InstanceFilter.EMPTY_ARRAY;
  }

  @Override
  public boolean isCountFilterEnabled() {
    return false;
  }

  @Override
  public int getCountFilter() {
    return 0;
  }

  @Override
  public boolean isClassFiltersEnabled() {
    return false;
  }

  @Override
  public ClassFilter[] getClassFilters() {
    return ClassFilter.EMPTY_ARRAY;
  }

  @Override
  public ClassFilter[] getClassExclusionFilters() {
    return ClassFilter.EMPTY_ARRAY;
  }

  @Override
  public boolean shouldIgnoreThreadFiltering() {
    return true;
  }
}
