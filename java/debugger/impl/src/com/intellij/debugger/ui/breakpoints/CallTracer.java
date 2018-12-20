// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.settings.TraceSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.classFilter.ClassFilter;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author egor
 */
public class CallTracer {
  private static final Logger LOG = Logger.getInstance(CallTracer.class);
  public static final Key<CallTracer> CALL_TRACER_KEY = Key.create("CALL_TRACER");

  private final EventRequestManager myRequestManager;
  private final DebugProcessImpl myDebugProcess;
  private final List<MethodEntryRequest> myEntryRequests = new ArrayList<>(1);
  private int myStartIndent = 0;

  public CallTracer(DebugProcessImpl debugProcess) {
    myDebugProcess = debugProcess;
    myRequestManager = debugProcess.getRequestsManager().getVMRequestManager();
  }

  public void start(EvaluationContextImpl context) {
    int indent = 0;
    try {
      ThreadReferenceProxyImpl thread = context.getSuspendContext().getThread();
      if (thread != null) {
        indent = thread.frameCount();
      }
    }
    catch (EvaluateException e) {
      LOG.error(e);
    }
    start(indent);
  }

  private void start(int startIndent) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myEntryRequests.isEmpty()) {
      myStartIndent = startIndent;
      TraceSettings traceSettings = TraceSettings.getInstance();
      ClassFilter[] classFilters = traceSettings.getClassFilters();
      ClassFilter[] exclusionFilters = traceSettings.getClassExclusionFilters();
      if (DebuggerUtilsEx.getEnabledNumber(classFilters) == 0) {
        addEntryRequest(null, exclusionFilters);
      }
      else {
        for (ClassFilter filter : classFilters) {
          if (filter.isEnabled()) {
            addEntryRequest(filter, exclusionFilters);
          }
        }
      }
    }
  }

  private void addEntryRequest(ClassFilter filter, ClassFilter[] exclusionFilters) {
    MethodEntryRequest request = myRequestManager.createMethodEntryRequest();
    request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); // to be able to detect frameCount inside the event handler
    myDebugProcess.getRequestsManager()
      .addClassFilters(request, filter != null ? new ClassFilter[]{filter} : ClassFilter.EMPTY_ARRAY, exclusionFilters);
    myEntryRequests.add(request);
    DebugProcessEvents.enableRequestWithHandler(request, this::accept);
  }

  public void stop() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (!myEntryRequests.isEmpty()) {
      myEntryRequests.forEach(myRequestManager::deleteEventRequest);
      myEntryRequests.clear();
    }
  }

  private boolean isActive() {
    return !myEntryRequests.isEmpty();
  }

  private void accept(Event event) {
    if (event instanceof MethodEntryEvent) {
      MethodEntryEvent methodEntryEvent = (MethodEntryEvent)event;
      try {
        ThreadReference thread = methodEntryEvent.thread();
        for (SuspendContextImpl context : myDebugProcess.getSuspendManager().getEventContexts()) {
          ThreadReferenceProxyImpl contextThread = context.getThread();
          if (context.isEvaluating() && contextThread != null && contextThread.getThreadReference().equals(thread)) {
            return; // evaluating - skip
          }
        }
        int indent = thread.frameCount() - myStartIndent;
        if (indent < 0) {
          stop();
          return;
        }
        myDebugProcess.printToConsole("\n" + StringUtil.repeat(" ", indent) + methodEntryEvent.method());
      }
      catch (IncompatibleThreadStateException e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  public static CallTracer get(DebugProcessImpl debugProcess) {
    CallTracer tracer = debugProcess.getUserData(CALL_TRACER_KEY);
    if (tracer == null) {
      tracer = new CallTracer(debugProcess);
      debugProcess.putUserData(CALL_TRACER_KEY, tracer);
    }
    return tracer;
  }

  public static class CallTracerToggleAction extends DumbAwareToggleAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(Registry.is("debugger.call.tracing"));
      super.update(e);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      DebugProcessImpl process = JavaDebugProcess.getCurrentDebugProcess(e.getProject());
      if (process != null) {
        CallTracer tracer = process.getUserData(CALL_TRACER_KEY);
        if (tracer != null) {
          return tracer.isActive();
        }
      }
      return false;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      DebugProcessImpl process = JavaDebugProcess.getCurrentDebugProcess(e.getProject());
      if (process != null) {
        CallTracer tracer = get(process);
        process.getManagerThread().schedule(PrioritizedTask.Priority.HIGH, () -> {
          if (state) {
            StackFrameProxyImpl frame = process.getDebuggerContext().getFrameProxy();
            if (frame != null) {
              tracer.start(frame.getIndexFromBottom());
            }
          }
          else {
            tracer.stop();
          }
        });
      }
    }
  }
}
