/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Alarm;
import com.intellij.util.SingleAlarm;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ThreadReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.HashSet;

/**
 * @author egor
 */
public class ThreadBlockedMonitor {
  private final Collection<ThreadReferenceProxy> myWatchedThreads = new HashSet<ThreadReferenceProxy>();

  private final SingleAlarm myAlarm;
  private final DebugProcessImpl myProcess;

  public ThreadBlockedMonitor(DebugProcessImpl process, Disposable disposable) {
    myProcess = process;
    myAlarm = new SingleAlarm(new Runnable() {
      @Override
      public void run() {
        checkBlockingThread();
      }
    }, 5000, Alarm.ThreadToUse.POOLED_THREAD, disposable);
  }

  public void startWatching(@Nullable ThreadReferenceProxy thread) {
    if (!Registry.is("debugger.monitor.blocked.threads")) return;
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (thread != null) {
      myWatchedThreads.add(thread);
      myAlarm.request();
    }
  }

  public void stopWatching(@Nullable ThreadReferenceProxy thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (thread != null) {
      myWatchedThreads.remove(thread);
    }
    if (myWatchedThreads.isEmpty()) {
      myAlarm.cancel();
    }
  }

  private void onThreadBlocked(@NotNull final ThreadReference blockedThread,
                               @NotNull final ThreadReference blockingThread,
                               final DebugProcessImpl process) {
    XDebugSessionImpl.NOTIFICATION_GROUP.createNotification(
      DebuggerBundle.message("status.thread.blocked.by", blockedThread.name(), blockingThread.name()),
      DebuggerBundle.message("status.thread.blocked.by.resume", blockingThread.name()),
      NotificationType.INFORMATION, new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
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
        }
      }).notify(process.getProject());
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
              if (blockingThread != null) {
                onThreadBlocked(thread.getThreadReference(), blockingThread, myProcess);
              }
            }
          }
        }
        catch (IncompatibleThreadStateException e) {
          e.printStackTrace();
        }
        finally {
          vmProxy.getVirtualMachine().resume();
          myAlarm.request();
        }
      }
    });
  }
}
