// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.DefaultDebugEnvironment;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class JavaTestFrameworkDebuggerRunner extends GenericDebuggerRunner {
  @Override
  public abstract @NotNull String getRunnerId();

  protected abstract boolean validForProfile(@NotNull RunProfile profile);
  
  protected abstract @NotNull String getThreadName();

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && validForProfile(profile);
  }

  @Override
  protected @Nullable RunContentDescriptor createContentDescriptor(final @NotNull RunProfileState state, final @NotNull ExecutionEnvironment environment)
    throws ExecutionException {
    final RunContentDescriptor res = super.createContentDescriptor(state, environment);
    final ServerSocket socket = ((JavaTestFrameworkRunnableState<?>)state).getForkSocket();
    if (socket != null) {
      Thread thread = new Thread(getThreadName() + " debugger runner") {
        @Override
        public void run() {
          try (Socket accept = socket.accept();
               DataInputStream stream = new DataInputStream(accept.getInputStream())) {
            int read = stream.readInt();
            while (read != -1) {
              final DebugProcess process =
                DebuggerManager.getInstance(environment.getProject()).getDebugProcess(res.getProcessHandler());
              if (process == null) break;
              final RemoteConnection connection = new RemoteConnection(true, "127.0.0.1", String.valueOf(read), true);
              final DebugEnvironment env = new DefaultDebugEnvironment(environment, state, connection, true);
              ((DebugProcessImpl)process).reattach(env, false, () -> {
                try {
                  accept.getOutputStream().write(0);
                }
                catch (Exception e) {
                  e.printStackTrace();
                }
              });
              read = stream.readInt();
            }
          }
          catch (EOFException ignored) {}
          catch (IOException e) {
            e.printStackTrace();
          }
        }
      };
      thread.setDaemon(true);
      thread.start();
    }
    return res;
  }
}
