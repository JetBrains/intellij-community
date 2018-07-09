/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.build.BuildView;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.remote.RemoteConfigurationType;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author Denis Zhdanov
 * @since 6/7/13 11:18 AM
 */
public class ExternalSystemTaskDebugRunner extends GenericDebuggerRunner {
  private static final Logger LOG = Logger.getInstance(ExternalSystemTaskDebugRunner.class);

  @NotNull
  @Override
  public String getRunnerId() {
    return ExternalSystemConstants.DEBUG_RUNNER_ID;
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return profile instanceof ExternalSystemRunConfiguration && DefaultDebugExecutor.EXECUTOR_ID.equals(executorId);
  }

  @Nullable
  @Override
  protected RunContentDescriptor createContentDescriptor(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment) throws ExecutionException {
    if (state instanceof ExternalSystemRunConfiguration.MyRunnableState) {
      ExternalSystemRunConfiguration.MyRunnableState myRunnableState = (ExternalSystemRunConfiguration.MyRunnableState)state;
      int port = myRunnableState.getDebugPort();
      if (port > 0) {
        RunContentDescriptor runContentDescriptor = doGetRunContentDescriptor(myRunnableState, environment, port);
        if (runContentDescriptor == null) return null;

        ProcessHandler processHandler = runContentDescriptor.getProcessHandler();
        final ServerSocket socket = myRunnableState.getForkSocket();
        if (socket != null && processHandler != null) {
          new ForkedDebuggerThread(processHandler, socket, environment.getProject()).start();
        }
        return runContentDescriptor;
      }
      else {
        LOG.warn("Can't attach debugger to external system task execution. Reason: target debug port is unknown");
      }
    }
    else {
      LOG.warn(String.format(
        "Can't attach debugger to external system task execution. Reason: invalid run profile state is provided"
        + "- expected '%s' but got '%s'",
        ExternalSystemRunConfiguration.MyRunnableState.class.getName(), state.getClass().getName()
      ));
    }
    return null;
  }

  @Nullable
  private RunContentDescriptor doGetRunContentDescriptor(@NotNull ExternalSystemRunConfiguration.MyRunnableState state,
                                                         @NotNull ExecutionEnvironment environment,
                                                         int port) throws ExecutionException {
    RemoteConnection connection = new RemoteConnection(true, "127.0.0.1", String.valueOf(port), true);
    RunContentDescriptor runContentDescriptor = attachVirtualMachine(state, environment, connection, true);
    if (runContentDescriptor == null) return null;

    state.setContentDescriptor(runContentDescriptor);

    ExecutionConsole executionConsole = runContentDescriptor.getExecutionConsole();
    if (executionConsole instanceof BuildView) {
      return runContentDescriptor;
    }
    RunContentDescriptor descriptor =
      new RunContentDescriptor(runContentDescriptor.getExecutionConsole(), runContentDescriptor.getProcessHandler(),
                               runContentDescriptor.getComponent(), runContentDescriptor.getDisplayName(),
                               runContentDescriptor.getIcon(), null,
                               runContentDescriptor.getRestartActions()) {
        @Override
        public boolean isHiddenContent() {
          return true;
        }
      };
    descriptor.setRunnerLayoutUi(runContentDescriptor.getRunnerLayoutUi());
    return descriptor;
  }

  private static class ForkedDebuggerThread extends Thread {
    private final ProcessHandler myProcessHandler;
    private final ServerSocket mySocket;
    private final Project myProject;

    public ForkedDebuggerThread(ProcessHandler processHandler, ServerSocket socket, Project project) {
      super("external task forked debugger runner");
      setDaemon(true);
      myProcessHandler = processHandler;
      mySocket = socket;
      myProject = project;
      myProcessHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          closeSocket();
        }

        @Override
        public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
          closeSocket();
        }

        void closeSocket() {
          try {
            mySocket.close();
          }
          catch (IOException ignore) {
          }
        }
      });
    }

    @Override
    public void run() {
      while (!myProcessHandler.isProcessTerminated() && !myProcessHandler.isProcessTerminating() && !mySocket.isClosed()) {
        try {
          Socket accept = mySocket.accept();
          handleForkedProcessSignal(accept, myProject, myProcessHandler);
        }
        catch (EOFException ignored) {
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      }
      try {
        mySocket.close();
      }
      catch (IOException e) {
        LOG.debug(e);
      }
    }

    public static void handleForkedProcessSignal(Socket accept, Project project, ProcessHandler processHandler) throws IOException {
      // the stream can not be closed in the current thread
      //noinspection IOResourceOpenedButNotSafelyClosed
      DataInputStream stream = new DataInputStream(accept.getInputStream());
      processHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          StreamUtil.closeStream(stream);
          StreamUtil.closeStream(accept);
        }
      });
      int signal = stream.readInt();
      String processName = stream.readUTF();
      if (signal > 0) {
        String debugPort = String.valueOf(signal);
        attachVM(project, processName, debugPort, new Callback() {
          @Override
          public void processStarted(RunContentDescriptor descriptor) {
            // select tab for the forked process only when it has been suspended
            descriptor.setSelectContentWhenAdded(false);

            // restore selection of the 'main' tab to avoid flickering of the reused content tab when no suspend events occur
            ProcessHandler processHandler = descriptor.getProcessHandler();
            if (processHandler != null) {
              processHandler.addProcessListener(new ProcessAdapter() {
                @Override
                public void processTerminated(@NotNull ProcessEvent event) {
                  final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
                  ContentManager contentManager = toolWindowManager.getToolWindow(ToolWindowId.DEBUG).getContentManager();
                  Content content = descriptor.getAttachedContent();
                  if (content != null) {
                    ApplicationManager.getApplication().invokeLater(() -> contentManager.removeContent(content, true));
                  }
                }
              });
              try {
                accept.getOutputStream().write(0);
                stream.close();
              }
              catch (IOException e) {
                LOG.debug(e);
              }
            }
          }
        });
      }
      else if (signal == 0) {
        // remove content for terminated forked processes
        ApplicationManager.getApplication().invokeLater(() -> {
          final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
          ContentManager contentManager = toolWindowManager.getToolWindow(ToolWindowId.DEBUG).getContentManager();
          Content content = contentManager.findContent(processName);
          if (content != null) {
            RunContentDescriptor descriptor = content.getUserData(RunContentDescriptor.DESCRIPTOR_KEY);
            if (descriptor != null) {
              ProcessHandler handler = descriptor.getProcessHandler();
              if (handler != null) {
                handler.destroyProcess();
              }
            }
          }
          try {
            accept.getOutputStream().write(0);
            stream.close();
          }
          catch (IOException e) {
            LOG.debug(e);
          }
        });
      }
    }

    private static void attachVM(@NotNull Project project, String runConfigName, @NotNull String debugPort, Callback callback) {
      RemoteConfigurationType remoteConfigurationType = RemoteConfigurationType.getInstance();
      ConfigurationFactory factory = remoteConfigurationType.getFactory();
      RunnerAndConfigurationSettings runSettings = RunManager.getInstance(project).createRunConfiguration(runConfigName, factory);
      runSettings.setActivateToolWindowBeforeRun(false);

      RemoteConfiguration configuration = (RemoteConfiguration)runSettings.getConfiguration();
      configuration.HOST = "localhost";
      configuration.PORT = debugPort;
      configuration.USE_SOCKET_TRANSPORT = true;
      configuration.SERVER_MODE = true;

      try {
        ExecutionEnvironment environment = ExecutionEnvironmentBuilder.create(DefaultDebugExecutor.getDebugExecutorInstance(), runSettings)
                                                                      .contentToReuse(null)
                                                                      .dataContext(null)
                                                                      .activeTarget()
                                                                      .build();
        ProgramRunnerUtil.executeConfigurationAsync(environment, true, true, callback);
      }
      catch (ExecutionException e) {
        LOG.error(e);
      }
    }
  }
}
