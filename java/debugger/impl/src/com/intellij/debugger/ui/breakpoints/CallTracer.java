// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerContextImpl;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author egor
 */
public class CallTracer {
  private static final Logger LOG = Logger.getInstance(CallTracer.class);
  public static final Key<CallTracer> CALL_TRACER_KEY = Key.create("CALL_TRACER");

  private final EventRequestManager myRequestManager;
  private final DebugProcessImpl myDebugProcess;
  private final Map<ThreadReference, ThreadRequest> myThreadRequests = new ConcurrentHashMap<>();

  public CallTracer(DebugProcessImpl debugProcess) {
    myDebugProcess = debugProcess;
    myRequestManager = debugProcess.getRequestsManager().getVMRequestManager();
  }

  public void start(@Nullable ThreadReferenceProxyImpl thread) {
    try {
      if (thread != null) {
        start(thread.getThreadReference(), thread.frameCount());
      }
    }
    catch (EvaluateException e) {
      LOG.error(e);
    }
  }

  private void start(@NotNull ThreadReference thread, int startIndent) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    myThreadRequests.computeIfAbsent(thread, t -> new ThreadRequest(t, startIndent));
  }

  public void stop(@NotNull ThreadReference thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ThreadRequest request = myThreadRequests.remove(thread);
    if (request != null) {
      request.stop();
    }
  }

  public void stopAll() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    List<ThreadRequest> requests = new ArrayList<>(myThreadRequests.values());
    myThreadRequests.clear();
    requests.forEach(ThreadRequest::stop);
  }

  private boolean isActive() {
    return !myThreadRequests.isEmpty();
  }

  private void accept(Event event) {
    if (event instanceof MethodEntryEvent) {
      MethodEntryEvent methodEntryEvent = (MethodEntryEvent)event;
      try {
        ThreadReference thread = methodEntryEvent.thread();
        ThreadRequest request = myThreadRequests.get(thread);
        if (request != null) {
          for (SuspendContextImpl context : myDebugProcess.getSuspendManager().getEventContexts()) {
            ThreadReferenceProxyImpl contextThread = context.getThread();
            if (context.isEvaluating() && contextThread != null && contextThread.getThreadReference().equals(thread)) {
              return; // evaluating - skip
            }
          }
          int indent = thread.frameCount() - request.myStartIndent;
          String indentString = indent < 0 ? "-" : StringUtil.repeat(" ", indent);
          myDebugProcess.printToConsole("\n" + indentString + methodEntryEvent.method() + " thread " + thread.uniqueID());
        }
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

  private class ThreadRequest {
    private final List<MethodEntryRequest> myEntryRequests = new ArrayList<>(1);
    private final int myStartIndent;

    private ThreadRequest(ThreadReference thread, int startIndent) {
      myStartIndent = startIndent;
      TraceSettings traceSettings = TraceSettings.getInstance();
      ClassFilter[] classFilters = traceSettings.getClassFilters();
      ClassFilter[] exclusionFilters = traceSettings.getClassExclusionFilters();
      if (DebuggerUtilsEx.getEnabledNumber(classFilters) == 0) {
        addEntryRequest(null, exclusionFilters, thread);
      }
      else {
        for (ClassFilter filter : classFilters) {
          if (filter.isEnabled()) {
            addEntryRequest(filter, exclusionFilters, thread);
          }
        }
      }
    }

    private void addEntryRequest(ClassFilter filter, ClassFilter[] exclusionFilters, ThreadReference thread) {
      MethodEntryRequest request = myRequestManager.createMethodEntryRequest();
      request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); // to be able to detect frameCount inside the event handler
      request.addThreadFilter(thread);
      myDebugProcess.getRequestsManager()
        .addClassFilters(request, filter != null ? new ClassFilter[]{filter} : ClassFilter.EMPTY_ARRAY, exclusionFilters);
      myEntryRequests.add(request);
      DebugProcessEvents.enableRequestWithHandler(request, CallTracer.this::accept);
    }

    void stop() {
      myEntryRequests.forEach(myRequestManager::deleteEventRequest);
    }
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
          DebuggerContextImpl debuggerContext = process.getDebuggerContext();
          ThreadReferenceProxyImpl threadProxy = debuggerContext.getThreadProxy();
          if (state) {
            StackFrameProxyImpl frame = debuggerContext.getFrameProxy();
            if (frame != null && threadProxy != null) {
              tracer.start(threadProxy.getThreadReference(), frame.getIndexFromBottom());
            }
          }
          else {
            if (threadProxy != null) {
              tracer.stop(threadProxy.getThreadReference());
            }
            else {
              tracer.stopAll();
            }
          }
        });
      }
    }
  }
}
