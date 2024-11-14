// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.concurrency.JobScheduler;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;
import com.intellij.debugger.jdi.JvmtiError;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
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
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThreadBlockedMonitor {
  private static final Logger LOG = Logger.getInstance(ThreadBlockedMonitor.class);

  private final Collection<ThreadReferenceProxy> myWatchedThreads = new HashSet<>();

  private ScheduledFuture<?> myTask;
  private final DebugProcessImpl myProcess;

  protected @Nullable InvocationWatcherNewImpl myInvocationWatching = null;
  private boolean myIsInResumeAllMode = false;

  public ThreadBlockedMonitor(DebugProcessImpl process, Disposable disposable) {
    myProcess = process;
    Disposer.register(disposable, this::cancelTask);
  }

  static boolean isNewSuspendAllInvocationWatcher() {
    return Registry.is("debugger.new.invocation.watcher");
  }

  static int getSingleThreadedEvaluationThreshold() {
    return Registry.intValue("debugger.evaluate.single.threaded.timeout", 1000);
  }

  @Nullable
  protected InvocationWatcher startInvokeWatching(int invokePolicy,
                                                  @Nullable ThreadReferenceProxyImpl thread,
                                                  @NotNull SuspendContextImpl context) {
    if (thread != null && getSingleThreadedEvaluationThreshold() > 0 &&
        context.getSuspendPolicy() == EventRequest.SUSPEND_ALL &&
        BitUtil.isSet(invokePolicy, ObjectReference.INVOKE_SINGLE_THREADED)) {
      if (isNewSuspendAllInvocationWatcher()) {
        return new InvocationWatcherNewImpl(this, thread, context);
      }
      else {
        return new InvocationWatcherOldImpl(this, thread);
      }
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
    XDebuggerManagerImpl.getNotificationGroup()
      .createNotification(JavaDebuggerBundle.message("status.thread.blocked.by", blockedThread.name(), blockingThread.name()),
                          JavaDebuggerBundle.message("status.thread.blocked.by.resume", blockingThread.name()),
                          NotificationType.INFORMATION)
      .setListener((notification, event) -> {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          notification.expire();
          process.getManagerThread().schedule(new DebuggerCommandImpl() {
            @Override
            protected void action() {
              ThreadReferenceProxyImpl threadProxy = process.getVirtualMachineProxy().getThreadReferenceProxy(blockingThread);
              SuspendContextImpl suspendingContext = SuspendManagerUtil.getSuspendingContext(process.getSuspendManager(), threadProxy);
              getCommandManagerThread()
                .invoke(process.createResumeThreadCommand(suspendingContext, threadProxy));
            }
          });
        }
      })
      .notify(process.getProject());
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
        vmProxy.suspend();
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
            catch (IncompatibleThreadStateException e) {
              LOG.info(e);
            }
            catch (InternalException e) {
              if (e.errorCode() != JvmtiError.THREAD_NOT_ALIVE) {
                throw e;
              }
            }
          }
        }
        finally {
          vmProxy.resume();
        }
      }
    });
  }

  protected boolean isInResumeAllMode() {
    return myInvocationWatching != null;
  }

  protected interface InvocationWatcher {
    void invocationFinished();
  }

  protected static final class InvocationWatcherNewImpl implements InvocationWatcher {
    private final AtomicBoolean myObsolete = new AtomicBoolean();
    private final AtomicBoolean myAllResumed = new AtomicBoolean();
    private final Future myTask;
    private @Nullable Future myDiagnosticsTask;
    private final @NotNull ThreadReferenceProxyImpl myThread;
    final SuspendContextImpl mySuspendAllContext;
    private final @NotNull DebugProcessImpl myProcess;
    private final @NotNull ThreadBlockedMonitor myThreadBlockedMonitor;

    private InvocationWatcherNewImpl(@NotNull ThreadBlockedMonitor threadBlockedMonitor, @NotNull ThreadReferenceProxyImpl thread,
                                     @NotNull SuspendContextImpl suspendAllContext) {
      myThreadBlockedMonitor = threadBlockedMonitor;
      myProcess = threadBlockedMonitor.myProcess;
      myThread = thread;
      mySuspendAllContext = suspendAllContext;
      myTask = JobScheduler.getScheduler().schedule(this::checkInvocation, getSingleThreadedEvaluationThreshold(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void invocationFinished() {
      myObsolete.set(true);
      if (myTask.isDone() && myAllResumed.get()) {
        // suspend all threads but the current one (which should be suspended already)
        myThread.getVirtualMachine().suspend();
        LOG.warn("Long invocation on " + myThread + " has been finished");
        myThreadBlockedMonitor.myInvocationWatching = null;
        myThread.resumeImpl();
        Set<ThreadReferenceProxyImpl> resumedThreads = mySuspendAllContext.myResumedThreads;
        if (resumedThreads != null) {
          for (ThreadReferenceProxyImpl thread : resumedThreads) {
            thread.resumeImpl();
          }
        }
        if (myDiagnosticsTask != null) {
          myDiagnosticsTask.cancel(false);
        }
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
          VirtualMachineProxyImpl virtualMachine = myThread.getVirtualMachine();
          virtualMachine.suspend();
          try {
            if (myObsolete.get()) return;
            if (myThreadBlockedMonitor.myInvocationWatching != null) {
              myProcess.logError("Another invocation on suspend-all thread " + myThread +
                                 " (" + mySuspendAllContext + ") while the previous one was not over yet " +
                                 myThreadBlockedMonitor.myInvocationWatching.mySuspendAllContext);
              return;
            }
            ThreadReference threadReference = myThread.getThreadReference();
            LOG.warn("Resume other threads because long invocation detected on " + myThread);
            myThreadBlockedMonitor.myInvocationWatching = InvocationWatcherNewImpl.this;
            myAllResumed.set(true);
            // resume all but this, this one is already resumed under evaluation
            myThread.suspendImpl();
            Set<ThreadReferenceProxyImpl> resumedThreads = mySuspendAllContext.myResumedThreads;
            if (resumedThreads != null) {
              for (ThreadReferenceProxyImpl thread : resumedThreads) {
                thread.suspendImpl();
              }
            }
            virtualMachine.resume();
            if (threadReference.suspendCount() != 1) {
              Set<SuspendContextImpl> suspendingContexts = SuspendManagerUtil.getSuspendingContexts(myProcess.getSuspendManager(), myThread);
              LOG.warn("Blocked thread detected during invocation on " + myThread + ": " + suspendingContexts);
            }
          }
          finally {
            virtualMachine.resume();
          }
          scheduleDiagnostics();
        }
      });
    }

    private void scheduleDiagnostics() {
      long delayToDiagnostics = getSingleThreadedEvaluationThreshold() * 10L;
      myDiagnosticsTask = JobScheduler.getScheduler().schedule(() -> {
        if (myObsolete.get()) return;
        myProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
          @Override
          protected void action() {
            if (myObsolete.get()) return;
            DebuggerDiagnosticsUtil.checkThreadsConsistency(myProcess, false);
            if (ApplicationManager.getApplication().isInternal()) {
              myProcess.logError("Internal error, some deadlock or just very long evaluation in the code: " +
                                 " Long invocation on " + myThread + " for " + mySuspendAllContext +
                                 " has not been finished for " + delayToDiagnostics + " ms.");
            }
          }
        });
      }, delayToDiagnostics, TimeUnit.MILLISECONDS);
    }
  }


  protected static final class InvocationWatcherOldImpl implements InvocationWatcher {
    private final AtomicBoolean myObsolete = new AtomicBoolean();
    private final AtomicBoolean myAllResumed = new AtomicBoolean();
    private final Future myTask;
    private final @NotNull ThreadReferenceProxyImpl myThread;
    private final @NotNull DebugProcessImpl myProcess;
    private final @NotNull ThreadBlockedMonitor myThreadBlockedMonitor;

    private InvocationWatcherOldImpl(@NotNull ThreadBlockedMonitor threadBlockedMonitor, @NotNull ThreadReferenceProxyImpl thread) {
      myThreadBlockedMonitor = threadBlockedMonitor;
      myProcess = threadBlockedMonitor.myProcess;
      myThread = thread;
      myTask = JobScheduler.getScheduler().schedule(this::checkInvocation, getSingleThreadedEvaluationThreshold(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void invocationFinished() {
      myObsolete.set(true);
      if (myTask.isDone() && myAllResumed.get()) {
        // suspend all threads but the current one (which should be suspended already
        myThread.getVirtualMachine().suspend();
        LOG.warn("Long invocation on " + myThread + " has been finished");
        myThreadBlockedMonitor.myIsInResumeAllMode = false;
        myThread.resumeImpl();
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
          VirtualMachineProxyImpl virtualMachine = myThread.getVirtualMachine();
          virtualMachine.suspend();
          try {
            if (myObsolete.get()) return;
            if (myThreadBlockedMonitor.myIsInResumeAllMode) {
              myProcess.logError("Another invocation on suspend-all thread while the previous one was not over yet");
              return;
            }
            ThreadReference threadReference = myThread.getThreadReference();
            if (threadReference.suspendCount() == 1) { // extra check for invocation in progress
              // resume all but this
              LOG.warn("Resume other threads because long invocation detected on " + myThread);
              myAllResumed.set(true);
              threadReference.suspend();
              virtualMachine.resume();
            }
            else {
              myProcess.logError("Blocked thread detected during invocation on " + myThread);
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
