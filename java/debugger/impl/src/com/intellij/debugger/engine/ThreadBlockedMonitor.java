// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.concurrency.JobScheduler;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.BitUtil;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.sun.jdi.*;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author egor
 */
public class ThreadBlockedMonitor {
  private static final Logger LOG = Logger.getInstance(ThreadBlockedMonitor.class);

  private final Collection<ThreadReferenceProxy> myWatchedThreads = new HashSet<>();

  private ScheduledFuture<?> myTask;
  private final DebugProcessImpl myProcess;

  public ThreadBlockedMonitor(DebugProcessImpl process, Disposable disposable) {
    myProcess = process;
    Disposer.register(disposable, this::cancelTask);
  }

  static int getSingleThreadedEvaluationThreshold() {
    return Registry.intValue("debugger.evaluate.single.threaded.timeout", 1000);
  }

  @Nullable
  public InvocationWatcher startInvokeWatching(int invokePolicy,
                                               @Nullable ThreadReferenceProxyImpl thread,
                                               @NotNull SuspendContextImpl context) {
    if (thread != null && getSingleThreadedEvaluationThreshold() > 0 &&
        context.getSuspendPolicy() == EventRequest.SUSPEND_ALL &&
        BitUtil.isSet(invokePolicy, ObjectReference.INVOKE_SINGLE_THREADED)) {
      return new InvocationWatcher(myProcess, thread);
    }
    return null;
  }

  public void startWatching(@Nullable ThreadReferenceProxy thread) {
    if (!Registry.is("debugger.monitor.blocked.threads")) return;
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (thread != null) {
      myWatchedThreads.add(thread);
      if (myTask == null) {
        myTask = JobScheduler.getScheduler().scheduleWithFixedDelay(this::checkBlockingThread, 5, 5, TimeUnit.SECONDS);
      }
    }
  }

  public void stopWatching(@Nullable ThreadReferenceProxy thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (thread != null) {
      myWatchedThreads.remove(thread);
    }
    else {
      myWatchedThreads.clear();
    }
    if (myWatchedThreads.isEmpty()) {
      cancelTask();
    }
  }

  private void cancelTask() {
    if (myTask != null) {
      myTask.cancel(true);
      myTask = null;
    }
  }

  private static void onThreadBlocked(@NotNull final ThreadReference blockedThread,
                                      @NotNull final ThreadReference blockingThread,
                                      final DebugProcessImpl process) {
    XDebuggerManagerImpl.NOTIFICATION_GROUP.createNotification(
      DebuggerBundle.message("status.thread.blocked.by", blockedThread.name(), blockingThread.name()),
      DebuggerBundle.message("status.thread.blocked.by.resume", blockingThread.name()),
      NotificationType.INFORMATION, (notification, event) -> {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          notification.expire();
          process.getManagerThread().schedule(new DebuggerCommandImpl() {
            @Override
            protected void action() {
              ThreadReferenceProxyImpl threadProxy = process.getVirtualMachineProxy().getThreadReferenceProxy(blockingThread);
              SuspendContextImpl suspendingContext = SuspendManagerUtil.getSuspendingContext(process.getSuspendManager(), threadProxy);
              process.getManagerThread()
                .invoke(process.createResumeThreadCommand(suspendingContext, threadProxy));
            }
          });
        }
      }).notify(process.getProject());
  }

  private ThreadReference getCurrentThread() {
    ThreadReferenceProxyImpl threadProxy = myProcess.getDebuggerContext().getThreadProxy();
    return threadProxy != null ? threadProxy.getThreadReference() : null;
  }

  private void checkBlockingThread() {
    myProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() {
        if (myWatchedThreads.isEmpty()) return;
        VirtualMachineProxyImpl vmProxy = myProcess.getVirtualMachineProxy();
        //TODO: can we do fast check without suspending all
        vmProxy.getVirtualMachine().suspend();
        try {
          for (ThreadReferenceProxy thread : myWatchedThreads) {
            try {
              ObjectReference waitedMonitor =
                vmProxy.canGetCurrentContendedMonitor() ? thread.getThreadReference().currentContendedMonitor() : null;
              if (waitedMonitor != null && vmProxy.canGetMonitorInfo()) {
                ThreadReference blockingThread = waitedMonitor.owningThread();
                if (blockingThread != null
                    && blockingThread.suspendCount() > 1
                    && getCurrentThread() != blockingThread) {
                  onThreadBlocked(thread.getThreadReference(), blockingThread, myProcess);
                }
              }
            }
            catch (ObjectCollectedException ignored) {
            }
          }
        }
        catch (IncompatibleThreadStateException e) {
          LOG.info(e);
        }
        finally {
          vmProxy.getVirtualMachine().resume();
        }
      }
    });
  }

  public static class InvocationWatcher {
    private final AtomicBoolean myObsolete = new AtomicBoolean();;
    private final AtomicBoolean myAllResumed = new AtomicBoolean();;
    private final Future myTask;
    private final ThreadReferenceProxyImpl myThread;
    private final DebugProcessImpl myProcess;

    private InvocationWatcher(DebugProcessImpl process, @NotNull ThreadReferenceProxyImpl thread) {
      myProcess = process;
      myThread = thread;
      myTask = JobScheduler.getScheduler().schedule(this::checkInvocation, getSingleThreadedEvaluationThreshold(), TimeUnit.MILLISECONDS);
    }

    void invocationFinished() {
      myObsolete.set(true);
      if (myTask.isDone() && myAllResumed.get()) {
        // suspend all threads but the current one (which should be suspended already
        myThread.getVirtualMachine().getVirtualMachine().suspend();
        myThread.getThreadReference().resume();
      }
      else {
        myTask.cancel(true);
      }
    }

    private void checkInvocation() {
      myProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
        @Override
        protected void action() {
          if (myObsolete.get()) return;
          VirtualMachine virtualMachine = myThread.getVirtualMachine().getVirtualMachine();
          virtualMachine.suspend();
          try {
            ThreadReference threadReference = myThread.getThreadReference();
            if (!myObsolete.get() && threadReference.suspendCount() == 1) { // extra check for invocation in progress
              // resume all but this
              myAllResumed.set(true);
              threadReference.suspend();
              virtualMachine.resume();
            }
          }
          finally {
            virtualMachine.resume();
          }
        }
      });
    }
  }
}
