// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.mockJDI.MockVirtualMachine;
import com.intellij.execution.ExecutionResult;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockDebugProcess extends DebugProcessEvents {
  private static final Logger LOG = Logger.getInstance(MockDebugProcess.class);

  private final MockVirtualMachine myVirtualMachine;

  public MockDebugProcess(Project project, MockVirtualMachine virtualMachine, Disposable disposable) {
    super(project);
    myVirtualMachine = virtualMachine;
    myState.set(State.ATTACHED);
    DebuggerManagerThreadImpl managerThread = getManagerThread();
    Disposer.register(disposable, () -> {
      try {
        managerThread.close();
        while (!managerThread.getCurrentRequest().isDone()) {
          UIUtil.dispatchAllInvocationEvents();
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
      finally {
        dispose();
      }
    });
    managerThread.invokeAndWait(new DebuggerCommandImpl() {
      @Override
      protected void action() {
        VirtualMachineProxyImpl vmProxy = new VirtualMachineProxyImpl(MockDebugProcess.this, myVirtualMachine);
        DebuggerManagerThreadImpl.getCurrentThread().setVmProxy(vmProxy);
      }
    });
  }

  @Override
  public @Nullable ExecutionResult attachVirtualMachine(@NotNull DebugEnvironment environment, @NotNull DebuggerSession session) {
    mySession = session;
    return null;
  }
}
