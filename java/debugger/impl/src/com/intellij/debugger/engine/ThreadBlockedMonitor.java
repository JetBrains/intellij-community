/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ThreadReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
            protected void action() throws Exception {
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
      protected void action() throws Exception {
        if (myWatchedThreads.isEmpty()) return;
        VirtualMachineProxyImpl vmProxy = myProcess.getVirtualMachineProxy();
        //TODO: can we do fast check without suspending all
        vmProxy.getVirtualMachine().suspend();
        try {
          for (ThreadReferenceProxy thread : myWatchedThreads) {
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
}
