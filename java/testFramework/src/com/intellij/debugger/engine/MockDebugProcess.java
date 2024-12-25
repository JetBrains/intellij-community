// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.DebugEnvironment;
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
  private VirtualMachineProxyImpl myVirtualMachineProxy;

  public MockDebugProcess(Project project, MockVirtualMachine virtualMachine, Disposable disposable) {
    super(project);
    myVirtualMachine = virtualMachine;
    myState.set(State.ATTACHED);
    Disposer.register(disposable, () -> {
      try {
        getManagerThread().close();
        while (!getManagerThread().getCurrentRequest().isDone()) {
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
  }

  @Override
  public @Nullable ExecutionResult attachVirtualMachine(@NotNull DebugEnvironment environment, @NotNull DebuggerSession session) {
    mySession = session;
    return null;
  }

  @Override
  public @NotNull VirtualMachineProxyImpl getVirtualMachineProxy() {
    if (myVirtualMachineProxy == null) {
      myVirtualMachineProxy = new VirtualMachineProxyImpl(this, myVirtualMachine);
    }
    return myVirtualMachineProxy;
  }
}
