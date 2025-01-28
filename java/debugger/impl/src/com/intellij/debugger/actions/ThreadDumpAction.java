// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.threadDumpParser.ThreadDumpParser;
import com.intellij.threadDumpParser.ThreadState;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.jdi.ThreadReferenceImpl;
import com.sun.jdi.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

public final class ThreadDumpAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();

    final DebuggerSession session = context.getDebuggerSession();
    if (session != null && session.isAttached()) {
      buildThreadStatesAsync(context)
        .thenAccept(threads -> {
          ApplicationManager.getApplication().invokeLater(() -> {
            XDebugSession xSession = session.getXDebugSession();
            if (xSession != null) {
              DebuggerUtilsEx.addThreadDump(project, threads, xSession.getUI(), session.getSearchScope());
            }
          }, ModalityState.nonModal());
        }).exceptionally(ex -> {
          if (ex instanceof ControlFlowException || ex instanceof CancellationException) return null;
          Logger.getInstance(ThreadDumpAction.class).error(ex);
          return null;
        });
    }
  }

  public static CompletableFuture<List<ThreadState>> buildThreadStatesAsync(@NotNull DebuggerContextImpl context) {
    CompletableFuture<List<ThreadState>> future = new CompletableFuture<>();
    final DebugProcessImpl process = context.getDebugProcess();
    Objects.requireNonNull(context.getManagerThread()).schedule(new DebuggerCommandImpl() {
      @Override
      protected void commandCancelled() {
        future.cancel(false);
      }

      @Override
      protected void action() {
        final VirtualMachineProxyImpl vm = process.getVirtualMachineProxy();
        vm.suspend();
        try {
          final List<ThreadState> threads = buildThreadStates(vm);
          future.complete(threads);
        }
        catch (Exception e) {
          future.completeExceptionally(e);
          throw e;
        }
        finally {
          vm.resume();
        }
      }
    });
    return future;
  }

  private static @Nullable Value getThreadField(@NotNull String fieldName,
                                                @NotNull ReferenceType threadType,
                                                @NotNull ThreadReference threadObj,
                                                @Nullable ReferenceType holderType,
                                                @Nullable ObjectReference holderObj) {

    var threadField = DebuggerUtils.findField(threadType, fieldName);
    if (threadField != null) {
      return threadObj.getValue(threadField);
    }

    if (holderType != null) {
      assert holderObj != null;
      var holderField = DebuggerUtils.findField(holderType, fieldName);
      if (holderField != null) {
        return holderObj.getValue(holderField);
      }
    }

    return null;
  }

  public static List<ThreadState> buildThreadStates(VirtualMachineProxyImpl vmProxy) {
    final List<ThreadReference> threads = vmProxy.getVirtualMachine().allThreads();
    final List<ThreadState> result = new ArrayList<>();
    final Map<String, ThreadState> nameToThreadMap = new HashMap<>();
    final Map<String, String> waitingMap = new HashMap<>(); // key 'waits_for' value
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

      buffer.append("\"").append(threadName).append("\"");
      ReferenceType threadType = threadReference.referenceType();
      if (threadType != null) {
        // Since Project Loom some of Thread's fields are encapsulated into FieldHolder,
        // so we try to look up fields in the thread itself and in its holder.
        ReferenceType holderType;
        ObjectReference holderObj;
        if (getThreadField("holder", threadType, threadReference, null, null) instanceof ObjectReference value) {
          holderObj = value;
          holderType = holderObj.referenceType();
        } else {
          holderObj = null;
          holderType = null;
        }

        if (getThreadField("daemon", threadType, threadReference, holderType, holderObj) instanceof BooleanValue value) {
          if (value.booleanValue()) {
            buffer.append(" daemon");
            threadState.setDaemon(true);
          }
        }

        if (getThreadField("priority", threadType, threadReference, holderType, holderObj) instanceof IntegerValue value) {
          buffer.append(" prio=").append(value.intValue());
        }

        if (getThreadField("tid", threadType, threadReference, holderType, holderObj) instanceof LongValue value) {
          buffer.append(" tid=0x").append(Long.toHexString(value.longValue()));
          buffer.append(" nid=NA");
        }
      }

      // Virtual threads might be included in the list of all threads (i.e., see JDWP's option includevirtualthreads).
      if (threadReference instanceof ThreadReferenceImpl impl && impl.isVirtual()) {
        buffer.append(" virtual");
        threadState.setVirtual(true);
      }

      //ThreadGroupReference groupReference = threadReference.threadGroup();
      //if (groupReference != null) {
      //  buffer.append(", ").append(JavaDebuggerBundle.message("threads.export.attribute.label.group", groupReference.name()));
      //}
      final String state = threadState.getState();
      if (state != null) {
        buffer.append(" ").append(state);
      }

      buffer.append("\n  java.lang.Thread.State: ").append(threadState.getJavaThreadState());

      try {
        if (vmProxy.canGetOwnedMonitorInfo() && vmProxy.canGetMonitorInfo()) {
          List<ObjectReference> list = threadReference.ownedMonitors();
          for (ObjectReference reference : list) {
            if (!vmProxy.canGetMonitorFrameInfo()) { // java 5 and earlier
              buffer.append("\n\t ").append(renderLockedObject(reference));
            }
            final List<ThreadReference> waiting = reference.waitingThreads();
            for (ThreadReference thread : waiting) {
              final String waitingThreadName = threadName(thread);
              waitingMap.put(waitingThreadName, threadName);
              buffer.append("\n\t blocks ").append(waitingThreadName);
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
              buffer.append("\n\t waiting for ").append(monitorOwningThreadName)
                .append(" to release lock on ").append(waitedMonitor);
            }
          }
        }

        final List<StackFrame> frames = threadReference.frames();
        hasEmptyStack = frames.isEmpty();

        final Int2ObjectMap<List<ObjectReference>> lockedAt = new Int2ObjectOpenHashMap<>();
        if (vmProxy.canGetMonitorFrameInfo()) {
          for (Object m : threadReference.ownedMonitorsAndFrames()) {
            if (m instanceof MonitorInfo info) { // see JRE-937
              final int stackDepth = info.stackDepth();
              List<ObjectReference> monitors;
              if ((monitors = lockedAt.get(stackDepth)) == null) {
                lockedAt.put(stackDepth, monitors = new SmartList<>());
              }
              monitors.add(info.monitor());
            }
          }
        }

        for (int i = 0, framesSize = frames.size(); i < framesSize; i++) {
          final StackFrame stackFrame = frames.get(i);
          try {
            final Location location = stackFrame.location();
            buffer.append("\n\t  ").append(renderLocation(location));

            final List<ObjectReference> monitors = lockedAt.get(i);
            if (monitors != null) {
              for (ObjectReference monitor : monitors) {
                buffer.append("\n\t  - ").append(renderLockedObject(monitor));
              }
            }
          }
          catch (InvalidStackFrameException e) {
            buffer.append("\n\t  Invalid stack frame: ").append(e.getMessage());
          }
        }
      }
      catch (IncompatibleThreadStateException e) {
        buffer.append("\n\t Incompatible thread state: thread not suspended");
      }
      threadState.setStackTrace(buffer.toString(), hasEmptyStack);
      ThreadDumpParser.inferThreadStateDetail(threadState);
    }

    for (String waiting : waitingMap.keySet()) {
      final ThreadState waitingThread = nameToThreadMap.get(waiting);
      final ThreadState awaitedThread = nameToThreadMap.get(waitingMap.get(waiting));
      if (waitingThread != null && awaitedThread != null) { //zombie
        awaitedThread.addWaitingThread(waitingThread);
      }
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

  private static String renderLockedObject(ObjectReference monitor) {
    return "locked " + renderObject(monitor);
  }

  public static String renderObject(ObjectReference monitor) {
    String monitorTypeName;
    try {
      monitorTypeName = monitor.referenceType().name();
    }
    catch (Throwable e) {
      monitorTypeName = "Error getting object type: '" + e.getMessage() + "'";
    }
    return "<0x" + Long.toHexString(monitor.uniqueID()) + "> (a " + monitorTypeName + ")";
  }

  private static String threadStatusToJavaThreadState(int status) {
    return switch (status) {
      case ThreadReference.THREAD_STATUS_MONITOR -> Thread.State.BLOCKED.name();
      case ThreadReference.THREAD_STATUS_NOT_STARTED -> Thread.State.NEW.name();
      case ThreadReference.THREAD_STATUS_RUNNING -> Thread.State.RUNNABLE.name();
      case ThreadReference.THREAD_STATUS_SLEEPING -> Thread.State.TIMED_WAITING.name();
      case ThreadReference.THREAD_STATUS_WAIT -> Thread.State.WAITING.name();
      case ThreadReference.THREAD_STATUS_ZOMBIE -> Thread.State.TERMINATED.name();
      case ThreadReference.THREAD_STATUS_UNKNOWN -> "unknown";
      default -> "undefined";
    };
  }

  private static String threadStatusToState(int status) {
    return switch (status) {
      case ThreadReference.THREAD_STATUS_MONITOR -> "waiting for monitor entry";
      case ThreadReference.THREAD_STATUS_NOT_STARTED -> "not started";
      case ThreadReference.THREAD_STATUS_RUNNING -> "runnable";
      case ThreadReference.THREAD_STATUS_SLEEPING -> "sleeping";
      case ThreadReference.THREAD_STATUS_WAIT -> "waiting";
      case ThreadReference.THREAD_STATUS_ZOMBIE -> "zombie";
      case ThreadReference.THREAD_STATUS_UNKNOWN -> "unknown";
      default -> "undefined";
    };
  }

  public static @NonNls String renderLocation(final Location location) {
    return "at " + DebuggerUtilsEx.getLocationMethodQName(location) +
           "(" + DebuggerUtilsEx.getSourceName(location, "Unknown Source") + ":" + DebuggerUtilsEx.getLineNumber(location, false) + ")";
  }

  private static String threadName(ThreadReference threadReference) {
    return threadReference.name() + "@" + threadReference.uniqueID();
  }


  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    DebuggerSession debuggerSession = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
    presentation.setEnabled(debuggerSession != null && debuggerSession.isAttached());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
