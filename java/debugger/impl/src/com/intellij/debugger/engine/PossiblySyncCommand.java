// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.jetbrains.jdi.VirtualMachineImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public abstract class PossiblySyncCommand extends SuspendContextCommandImpl {
  public static final int DELAY = ApplicationManager.getApplication().isUnitTestMode() ? -1 : 50;

  protected PossiblySyncCommand(@Nullable SuspendContextImpl suspendContext) {
    super(suspendContext);
  }

  @Override
  public final void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
    if (!rescheduleIfNotIdle(suspendContext)) {
      syncAction(suspendContext);
    }
  }

  public abstract void syncAction(@NotNull SuspendContextImpl suspendContext);

  private boolean rescheduleIfNotIdle(@NotNull SuspendContextImpl suspendContext) {
    if (DELAY < 0) {
      return false;
    }
    DebugProcess process = suspendContext.getDebugProcess();
    DebuggerManagerThreadImpl managerThread = ((DebuggerManagerThreadImpl)process.getManagerThread());
    if (!managerThread.hasAsyncCommands() &&
        ((VirtualMachineImpl)((VirtualMachineProxyImpl)process.getVirtualMachineProxy()).getVirtualMachine()).isIdle()) {
      return false;
    }
    else {
      // reschedule with a small delay
      hold();
      AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> managerThread.schedule(this), DELAY, TimeUnit.MILLISECONDS);
      return true;
    }
  }
}
