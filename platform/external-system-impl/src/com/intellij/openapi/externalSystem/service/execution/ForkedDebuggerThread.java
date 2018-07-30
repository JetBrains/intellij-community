// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.remote.RemoteConfigurationType;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author Vladislav.Soroka
 */
class ForkedDebuggerThread extends Thread {
  @NotNull
  private final ProcessHandler myProcessHandler;
  @NotNull
  private final ServerSocket mySocket;
  @NotNull
  private final Project myProject;
  private final ConsoleView myConsole;

  public ForkedDebuggerThread(@NotNull ProcessHandler processHandler,
                              @NotNull RunContentDescriptor runContentDescriptor,
                              @NotNull ServerSocket socket,
                              @NotNull Project project) {
    super("external task forked debuggJavaForkOptionser runner");
    setDaemon(true);
    myProcessHandler = processHandler;
    mySocket = socket;
    myProject = project;
    myConsole = runContentDescriptor.getExecutionConsole() instanceof ConsoleView ?
                (ConsoleView)runContentDescriptor.getExecutionConsole() : null;
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
          if (!mySocket.isClosed()) {
            mySocket.close();
          }
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
        handleForkedProcessSignal(mySocket.accept());
      }
      catch (EOFException ignored) {
      }
      catch (IOException e) {
        ExternalSystemTaskDebugRunner.LOG.warn(e);
      }
    }
    try {
      if (!mySocket.isClosed()) {
        mySocket.close();
      }
    }
    catch (IOException e) {
      ExternalSystemTaskDebugRunner.LOG.debug(e);
    }
  }

  private void handleForkedProcessSignal(Socket accept) throws IOException {
    // the stream can not be closed in the current thread
    //noinspection IOResourceOpenedButNotSafelyClosed
    DataInputStream stream = new DataInputStream(accept.getInputStream());
    myProcessHandler.addProcessListener(new ProcessAdapter() {
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
      attachVM(myProject, processName, debugPort, new ProgramRunner.Callback() {
        @Override
        public void processStarted(RunContentDescriptor descriptor) {
          // select tab for the forked process only when it has been suspended
          descriptor.setSelectContentWhenAdded(false);

          // restore selection of the 'main' tab to avoid flickering of the reused content tab when no suspend events occur
          ProcessHandler forkedProcessHandler = descriptor.getProcessHandler();
          if (forkedProcessHandler != null) {
            myProcessHandler.addProcessListener(new ProcessAdapter() {
              @Override
              public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
                myProcessHandler.removeProcessListener(this);
                if (willBeDestroyed) {
                  forkedProcessHandler.destroyProcess();
                }
                else {
                  forkedProcessHandler.detachProcess();
                }
              }
            });

            forkedProcessHandler.addProcessListener(new MyForkedProcessListener(descriptor, processName));
            try {
              accept.getOutputStream().write(0);
              stream.close();
            }
            catch (IOException e) {
              ExternalSystemTaskDebugRunner.LOG.debug(e);
            }
          }
        }
      });
    }
    else if (signal == 0) {
      // remove content for terminated forked processes
      ApplicationManager.getApplication().invokeLater(() -> {
        final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
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
          ExternalSystemTaskDebugRunner.LOG.debug(e);
        }
      });
    }
  }

  private static void attachVM(@NotNull Project project, String runConfigName, @NotNull String debugPort, ProgramRunner.Callback callback) {
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
      ExternalSystemTaskDebugRunner.LOG.error(e);
    }
  }

  private class MyForkedProcessListener extends ProcessAdapter {
    @NotNull private final RunContentDescriptor myDescriptor;
    @NotNull private final String myProcessName;
    @Nullable private RangeHighlighter myHyperlink;

    public MyForkedProcessListener(@NotNull RunContentDescriptor descriptor, @NotNull String processName) {
      myDescriptor = descriptor;
      myProcessName = processName;
    }

    @Override
    public void startNotified(@NotNull ProcessEvent event) {
      postLink();
    }

    @Override
    public void processTerminated(@NotNull ProcessEvent event) {
      final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
      ContentManager contentManager = toolWindowManager.getToolWindow(ToolWindowId.DEBUG).getContentManager();
      Content content = myDescriptor.getAttachedContent();
      if (content != null) {
        ApplicationManager.getApplication().invokeLater(() -> contentManager.removeContent(content, true));
      }

      removeLink();
    }

    private void postLink() {
      ProcessHandler handler = myDescriptor.getProcessHandler();
      String addressDisplayName = "";
      DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(handler);
      if (debugProcess instanceof DebugProcessImpl) {
        addressDisplayName = "(" + DebuggerBundle.getAddressDisplayName(((DebugProcessImpl)debugProcess).getConnection()) + ")";
      }
      String linkText = ExternalSystemBundle.message("debugger.open.session.tab");
      String msg = ExternalSystemBundle.message("debugger.status.connected", myProcessName, addressDisplayName) + ". " + linkText + '\n';
      if (myConsole instanceof DataProvider) {
        Object executionConsole = ((DataProvider)myConsole).getData(LangDataKeys.CONSOLE_VIEW.getName());
        if (executionConsole instanceof ConsoleViewImpl) {
          int contentSize = myConsole.getContentSize();
          myConsole.print(msg, ConsoleViewContentType.SYSTEM_OUTPUT);
          myConsole.performWhenNoDeferredOutput(() -> {
            EditorHyperlinkSupport hyperlinkSupport = ((ConsoleViewImpl)executionConsole).getHyperlinks();
            int startOffset = contentSize + msg.indexOf(linkText) - 1;
            myHyperlink = hyperlinkSupport.createHyperlink(startOffset, startOffset + linkText.length(), null, project -> {
              // open tab
              final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
              ContentManager contentManager = toolWindowManager.getToolWindow(ToolWindowId.DEBUG).getContentManager();
              Content content = contentManager.findContent(myProcessName);
              if (content != null) {
                contentManager.setSelectedContent(content, true, true);
              }
            });
          });
        }
      }
    }

    private void removeLink() {
      if (myHyperlink != null && myConsole instanceof DataProvider) {
        Object executionConsole = ((DataProvider)myConsole).getData(LangDataKeys.CONSOLE_VIEW.getName());
        if (executionConsole instanceof ConsoleViewImpl) {
          ApplicationManager.getApplication().invokeLater(
            () -> {
              EditorHyperlinkSupport hyperlinkSupport = ((ConsoleViewImpl)executionConsole).getHyperlinks();
              int startOffset = myHyperlink.getStartOffset();
              int endOffset = myHyperlink.getEndOffset();
              TextAttributes inactiveTextAttributes = myHyperlink.getTextAttributes() != null
                                                      ? myHyperlink.getTextAttributes().clone() : TextAttributes.ERASE_MARKER.clone();
              inactiveTextAttributes.setForegroundColor(UIUtil.getInactiveTextColor());
              inactiveTextAttributes.setEffectColor(UIUtil.getInactiveTextColor());
              inactiveTextAttributes.setFontType(Font.ITALIC);
              hyperlinkSupport.removeHyperlink(myHyperlink);
              hyperlinkSupport.addHighlighter(startOffset, endOffset, inactiveTextAttributes, HighlighterLayer.CONSOLE_FILTER);
            }
          );
        }
      }
    }
  }
}
