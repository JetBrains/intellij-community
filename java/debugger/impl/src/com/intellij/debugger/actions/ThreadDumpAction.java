/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/**
 * class ExportThreadsAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.debugger.ui.DebuggerSessionTab;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.unscramble.ThreadDumpParser;
import com.intellij.unscramble.ThreadState;
import com.sun.jdi.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThreadDumpAction extends AnAction implements AnAction.TransparentUpdate {

  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }
    DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();

    if(context.getDebuggerSession() != null) {
      final DebugProcessImpl process = context.getDebugProcess();
      process.getManagerThread().invoke(new DebuggerCommandImpl() {
        protected void action() throws Exception {
          final VirtualMachineProxyImpl vm = process.getVirtualMachineProxy();
          vm.suspend();
          try {
            final List<ThreadState> threads = buildThreadStates(vm);
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                final DebuggerSessionTab sessionTab = DebuggerPanelsManager.getInstance(project).getSessionTab();
                if (sessionTab != null) {
                  sessionTab.addThreadDump(threads);
                }
              }
            }, ModalityState.NON_MODAL);
          }
          finally {
            vm.resume();
          }
        }
      });
    }
  }

  private static List<ThreadState> buildThreadStates(VirtualMachineProxyImpl vmProxy) {
    final List<ThreadReference> threads = vmProxy.getVirtualMachine().allThreads();
    final List<ThreadState> result = new ArrayList<ThreadState>();
    final Map<String, ThreadState> nameToThreadMap = new HashMap<String, ThreadState>();
    final Map<String, String> waitingMap = new HashMap<String, String>(); // key 'waits_for' value
    for (ThreadReference threadReference : threads) {
      final StringBuilder buffer = new StringBuilder();
      boolean hasEmptyStack = true;
      final int threadStatus = threadReference.status();
      if (threadStatus == ThreadReference.THREAD_STATUS_ZOMBIE) {
        continue;
      }
      final String threadName = threadName(threadReference);
      final ThreadState threadState = new ThreadState(threadName, threadStatusToState(threadStatus));
      nameToThreadMap.put(threadName, threadState);
      result.add(threadState);
      threadState.setJavaThreadState(threadStatusToJavaThreadState(threadStatus));

      buffer.append(threadName);
      ReferenceType referenceType = threadReference.referenceType();
      if (referenceType != null) {
        //noinspection HardCodedStringLiteral
        Field daemon = referenceType.fieldByName("daemon");
        if (daemon != null) {
          Value value = threadReference.getValue(daemon);
          if (value instanceof BooleanValue && ((BooleanValue)value).booleanValue()) {
            buffer.append(" ").append(DebuggerBundle.message("threads.export.attribute.label.daemon"));
            threadState.setDaemon(true);
          }
        }

        //noinspection HardCodedStringLiteral
        Field priority = referenceType.fieldByName("priority");
        if (priority != null) {
          Value value = threadReference.getValue(priority);
          if (value instanceof IntegerValue) {
            buffer.append(", ").append(DebuggerBundle.message("threads.export.attribute.label.priority", ((IntegerValue)value).intValue()));
          }
        }
      }

      ThreadGroupReference groupReference = threadReference.threadGroup();
      if (groupReference != null) {
        buffer.append(", ").append(DebuggerBundle.message("threads.export.attribute.label.group", groupReference.name()));
      }
      buffer.append(", ").append(
        DebuggerBundle.message("threads.export.attribute.label.status", threadState.getState()));

      buffer.append("\n  java.lang.Thread.State: ").append(threadState.getJavaThreadState());
      
      try {
        if (vmProxy.canGetOwnedMonitorInfo() && vmProxy.canGetMonitorInfo()) {
          List<ObjectReference> list = threadReference.ownedMonitors();
          for (ObjectReference reference : list) {
            final List<ThreadReference> waiting = reference.waitingThreads();
            for (ThreadReference thread : waiting) {
              final String waitingThreadName = threadName(thread);
              waitingMap.put(waitingThreadName, threadName);
              buffer.append("\n\t ").append(DebuggerBundle.message("threads.export.attribute.label.blocks.thread", waitingThreadName));
            }
          }
        }

        ObjectReference waitedMonitor = vmProxy.canGetCurrentContendedMonitor() ? threadReference.currentContendedMonitor() : null;
        if (waitedMonitor != null) {
          if (vmProxy.canGetMonitorInfo()) {
            ThreadReference waitedMonitorOwner = waitedMonitor.owningThread();
            if (waitedMonitorOwner != null) {
              final String monitorOwningThreadName = threadName(waitedMonitorOwner);
              waitingMap.put(threadName, monitorOwningThreadName);
              buffer.append("\n\t ")
                .append(DebuggerBundle.message("threads.export.attribute.label.waiting.for.thread", monitorOwningThreadName));
            }
          }
        }

        final List<StackFrame> frames = threadReference.frames();
        hasEmptyStack = frames.size() == 0;
        for (StackFrame stackFrame : frames) {
          final Location location = stackFrame.location();
          buffer.append("\n\t  ").append(renderLocation(location));
        }
      }
      catch (IncompatibleThreadStateException e) {
        buffer.append("\n\t ").append(DebuggerBundle.message("threads.export.attribute.error.incompatible.state"));
      }
      threadState.setStackTrace(buffer.toString(), hasEmptyStack);
      ThreadDumpParser.inferThreadStateDetail(threadState);
    }

    for (String waiting : waitingMap.keySet()) {
      final ThreadState waitingThread = nameToThreadMap.get(waiting);
      final ThreadState awaitedThread = nameToThreadMap.get(waitingMap.get(waiting));
      awaitedThread.addWaitingThread(waitingThread);
    }

    // detect simple deadlocks
    for (ThreadState thread : result) {
      for (ThreadState awaitingThread : thread.getAwaitingThreads()) {
        if (awaitingThread.isAwaitedBy(thread)) {
          thread.addDeadlockedThread(awaitingThread);
          awaitingThread.addDeadlockedThread(thread);
        }
      }
    }

    ThreadDumpParser.sortThreads(result);
    return result;
  }

  private static String threadStatusToJavaThreadState(int status) {
    switch (status) {
      case ThreadReference.THREAD_STATUS_MONITOR:
        return Thread.State.BLOCKED.name();
      case ThreadReference.THREAD_STATUS_NOT_STARTED:
        return Thread.State.NEW.name();
      case ThreadReference.THREAD_STATUS_RUNNING:
        return Thread.State.RUNNABLE.name();
      case ThreadReference.THREAD_STATUS_SLEEPING:
        return Thread.State.TIMED_WAITING.name();
      case ThreadReference.THREAD_STATUS_WAIT:
        return Thread.State.WAITING.name();
      case ThreadReference.THREAD_STATUS_ZOMBIE:
        return Thread.State.TERMINATED.name();
      case ThreadReference.THREAD_STATUS_UNKNOWN:
        return "unknown";
      default:
        return "undefined";
    }
  }

  private static String threadStatusToState(int status) {
    switch (status) {
      case ThreadReference.THREAD_STATUS_MONITOR:
        return "waiting for monitor entry";
      case ThreadReference.THREAD_STATUS_NOT_STARTED:
        return "not started";
      case ThreadReference.THREAD_STATUS_RUNNING:
        return "runnable";
      case ThreadReference.THREAD_STATUS_SLEEPING:
        return "sleeping";
      case ThreadReference.THREAD_STATUS_WAIT:
        return "waiting";
      case ThreadReference.THREAD_STATUS_ZOMBIE:
        return "zombie";
      case ThreadReference.THREAD_STATUS_UNKNOWN:
        return "unknown";
      default:
        return "undefined";
    }
  }

  private static String renderLocation(final Location location) {
    String sourceName;
    try {
      sourceName = location.sourceName();
    }
    catch (AbsentInformationException e) {
      sourceName = "Unknown Source";
    }
    return DebuggerBundle.message(
        "export.threads.stackframe.format",
        location.declaringType().name() + "." + location.method().name(),
        sourceName,
        location.lineNumber()
    );
  }

  private static String threadName(ThreadReference threadReference) {
    return threadReference.name() + "@" + threadReference.uniqueID();
  }


  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    DebuggerSession debuggerSession = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
    presentation.setEnabled(debuggerSession != null);
  }
}