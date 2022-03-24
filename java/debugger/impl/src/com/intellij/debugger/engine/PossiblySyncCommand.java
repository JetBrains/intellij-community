// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.jetbrains.jdi.VirtualMachineImpl;
import com.sun.jdi.VirtualMachine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public abstract class PossiblySyncCommand extends SuspendContextCommandImpl {
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
    int delay = ApplicationManager.getApplication().isUnitTestMode() ? -1 : Registry.intValue("debugger.sync.commands.reschedule.delay");
    if (delay < 0) {
      return false;
    }
    DebugProcess process = suspendContext.getDebugProcess();
    DebuggerManagerThreadImpl managerThread = ((DebuggerManagerThreadImpl)process.getManagerThread());
    VirtualMachine virtualMachine = ((VirtualMachineProxyImpl)process.getVirtualMachineProxy()).getVirtualMachine();
    if (!(virtualMachine instanceof VirtualMachineImpl) ||
        !managerThread.hasAsyncCommands() && ((VirtualMachineImpl)virtualMachine).isIdle()) {
      return false;
    }
    else {
      // reschedule with a small delay
      hold();
      AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> managerThread.schedule(this), delay, TimeUnit.MILLISECONDS);
      return true;
    }
  }
}
