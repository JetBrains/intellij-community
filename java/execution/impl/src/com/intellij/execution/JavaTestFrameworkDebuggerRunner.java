// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  @Override
  public abstract String getRunnerId();

  protected abstract boolean validForProfile(@NotNull RunProfile profile);
  
  @NotNull
  protected abstract String getThreadName();

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && validForProfile(profile);
  }

  @Nullable
  @Override
  protected RunContentDescriptor createContentDescriptor(@NotNull final RunProfileState state, @NotNull final ExecutionEnvironment environment)
    throws ExecutionException {
    final RunContentDescriptor res = super.createContentDescriptor(state, environment);
    final ServerSocket socket = ((JavaTestFrameworkRunnableState)state).getForkSocket();
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
