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
package com.intellij.execution;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.DefaultDebugEnvironment;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaTestFrameworkRunnableState;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
          try {
            final Socket accept = socket.accept();
            try {
              DataInputStream stream = new DataInputStream(accept.getInputStream());
              try {
                int read = stream.readInt();
                while (read != -1) {
                  final DebugProcess process =
                    DebuggerManager.getInstance(environment.getProject()).getDebugProcess(res.getProcessHandler());
                  if (process == null) break;
                  final RemoteConnection connection = new RemoteConnection(true, "127.0.0.1", String.valueOf(read), true);
                  final DebugEnvironment env = new DefaultDebugEnvironment(environment, state, connection, true);
                  SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                      try {
                        ((DebugProcessImpl)process).reattach(env);
                        accept.getOutputStream().write(0);
                      }
                      catch (Exception e) {
                        e.printStackTrace();
                      }
                    }
                  });
                  read = stream.readInt();
                }
              } finally {
                stream.close();
              }
            } finally {
              accept.close();
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
