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
import com.intellij.debugger.ui.overhead.OverheadProducer;
import com.intellij.debugger.ui.overhead.OverheadTimings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.classFilter.ClassFilter;
import com.sun.jdi.*;
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
public class CallTracer implements OverheadProducer {
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

  private void accept(Event event) {
    OverheadTimings.add(myDebugProcess, this, 1, null);
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
          Method method = methodEntryEvent.method();
          StringBuilder res = new StringBuilder("\n");
          res.append(indentString).append(method.declaringType().name()).append('.').append(method.name()).append('(');
          if (Registry.is("debugger.call.tracing.arguments")) {
            StackFrame frame = thread.frame(0);
            boolean first = true;
            for (Value value : DebuggerUtilsEx.getArgumentValues(frame)) {
              if (!first) {
                res.append(", ");
              }
              first = false;
              if (value == null) {
                res.append("null");
              }
              else if (value instanceof StringReference) {
                res.append(((StringReference)value).value());
              }
              else if (value instanceof ObjectReference) {
                ObjectReference objectReference = (ObjectReference)value;
                res.append(StringUtil.getShortName(objectReference.referenceType().name())).append("@").append(objectReference.uniqueID());
              }
              else {
                res.append(value.toString());
              }
            }
          }
          else {
            boolean first = true;
            for (String typeName : method.argumentTypeNames()) {
              if (!first) {
                res.append(", ");
              }
              first = false;
              res.append(StringUtil.getShortName(typeName));
            }
          }
          res.append(')').append(" thread ").append(thread.uniqueID());
          myDebugProcess.printToConsole(res.toString());
        }
      }
      catch (VMDisconnectedException vmd) {
        throw vmd;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public boolean isEnabled() {
    return !myThreadRequests.isEmpty();
  }

  @Override
  public void setEnabled(boolean state) {
    myDebugProcess.getManagerThread().schedule(PrioritizedTask.Priority.HIGH, () -> {
      DebuggerContextImpl debuggerContext = myDebugProcess.getDebuggerContext();
      ThreadReferenceProxyImpl threadProxy = debuggerContext.getThreadProxy();
      if (state) {
        StackFrameProxyImpl frame = debuggerContext.getFrameProxy();
        if (frame != null && threadProxy != null) {
          start(threadProxy.getThreadReference(), frame.getIndexFromBottom());
        }
      }
      else {
        if (threadProxy != null) {
          stop(threadProxy.getThreadReference());
        }
        else {
          stopAll();
        }
      }
    });
  }

  @Override
  public void customizeRenderer(SimpleColoredComponent renderer) {
    renderer.append("Call Tracer");
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
          return tracer.isEnabled();
        }
      }
      return false;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      DebugProcessImpl process = JavaDebugProcess.getCurrentDebugProcess(e.getProject());
      if (process != null) {
        get(process).setEnabled(state);
      }
    }
  }
}
