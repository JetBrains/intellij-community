// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.build.BuildView;
import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.DefaultDebugEnvironment;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.debugger.engine.managerThread.DebuggerCommand;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.externalSystem.debugger.DebuggerBackendExtension;
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.NamedColorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.function.Consumer;

import static com.intellij.openapi.externalSystem.debugger.DebuggerBackendExtension.RUNTIME_MODULE_DIR_KEY;

/**
 * @author Vladislav.Soroka
 */
class ForkedDebuggerThread extends Thread {
  private final @NotNull ProcessHandler myMainProcessHandler;
  private final @NotNull ServerSocket mySocket;
  private final @NotNull Project myProject;
  private final @NotNull RunContentDescriptor myMainRunContentDescriptor;
  private final @NotNull ExecutionEnvironment myMainExecutionEnvironment;
  private final @NotNull ExternalSystemRunnableState myMainRunnableState;

  ForkedDebuggerThread(@NotNull ProcessHandler mainProcessHandler,
                       @NotNull RunContentDescriptor mainRunContentDescriptor,
                       @NotNull ServerSocket socket,
                       @NotNull ExecutionEnvironment mainExecutionEnvironment,
                       @NotNull ExternalSystemRunnableState mainRunnableState) {
    super("external task forked debugger runner");
    setDaemon(true);
    mySocket = socket;
    myProject = mainExecutionEnvironment.getProject();

    myMainProcessHandler = mainProcessHandler;
    myMainRunContentDescriptor = mainRunContentDescriptor;
    myMainExecutionEnvironment = mainExecutionEnvironment;
    myMainRunnableState = mainRunnableState;

    myMainProcessHandler.addProcessListener(new ProcessListener() {
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
    while (!myMainProcessHandler.isProcessTerminated() && !myMainProcessHandler.isProcessTerminating() && !mySocket.isClosed()) {
      try {
        if (ExternalSystemTaskDebugRunner.LOG.isDebugEnabled()) {
          int port = mySocket.getLocalPort();
          String host = mySocket.getInetAddress().getHostAddress();
          String productName = ApplicationNamesInfo.getInstance().getFullProductName();
          ExternalSystemTaskDebugRunner.LOG.debug(String.format("%s wait for debug process signal on '%s:%d'", productName, host, port));
        }
        handleForkedProcessSignal(mySocket.accept());
      }
      catch (EOFException ignored) {
      }
      catch (SocketException e) {
        ExternalSystemTaskDebugRunner.LOG.debug(e);
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
    DataInputStream stream = new DataInputStream(accept.getInputStream());

    String debuggerId = stream.readUTF();
    String processName = stream.readUTF();
    String processParameters = stream.readUTF();

    if (ExternalSystemTaskDebugRunner.LOG.isDebugEnabled()) {
      String productName = ApplicationNamesInfo.getInstance().getFullProductName();
      String logMessage = "%s received debug process signal ID='%s', PROC_NAME='%s', PARAMS='%s'";
      ExternalSystemTaskDebugRunner.LOG.debug(String.format(logMessage, productName, debuggerId, processName, processParameters));
    }

    if (processParameters.startsWith(ForkedDebuggerHelper.FINISH_PARAMS)) {
      removeTerminatedForks(processName, accept, stream);
      return;
    }

    myMainProcessHandler.addProcessListener(new ProcessListener() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        StreamUtil.closeStream(stream);
        StreamUtil.closeStream(accept);
      }
    });

    attachRemoteDebugger(debuggerId, processName, processParameters, accept, stream);
  }

  private void attachRemoteDebugger(
    @NotNull String debuggerId,
    @NotNull String processName,
    @NotNull String processParameters,
    @NotNull Socket accept,
    @NotNull DataInputStream inputStream
  ) {
    DebuggerBackendExtension extension = DebuggerBackendExtension.EP_NAME.findFirstSafe(it -> it.id().equals(debuggerId));
    if (extension != null) {
      RunnerAndConfigurationSettings settings = extension.debugConfigurationSettings(myProject, processName, processParameters);
      RunConfiguration runConfiguration = settings.getConfiguration();
      if (myMainRunnableState.isReattachDebugProcess()) {
        if (runConfiguration instanceof RemoteConfiguration) {
          reattachRemoteDebugger((RemoteConfiguration)runConfiguration, debugProcess -> {
            stopForkedProcessWhenMainProcessTerminated(debugProcess.getProcessHandler());
            initTerminateForkedProcessHandler(debugProcess.getProcessHandler());
            unblockRemote(accept, inputStream);
          });
          return;
        }
        ExternalSystemTaskDebugRunner.LOG.warn("Unsupported reattach child debugger process into main process");
      }
      runDebugConfiguration(settings, descriptor -> {
        // select tab for the forked process only when it has been suspended
        descriptor.setSelectContentWhenAdded(false);

        // restore selection of the 'main' tab to avoid flickering of the reused content tab when no suspend events occur
        stopForkedProcessWhenMainProcessTerminated(descriptor.getProcessHandler());
        removeRunContentWhenProcessIsTerminated(descriptor);
        initForkedProcessLogger(descriptor, processName);
        initTerminateForkedProcessHandler(descriptor.getProcessHandler());
        unblockRemote(accept, inputStream);
      });
    }
  }

  private void reattachRemoteDebugger(@NotNull RemoteConfiguration runConfiguration, @NotNull Consumer<? super DebugProcess> callback) {
    DebuggerManager debuggerManager = DebuggerManager.getInstance(myProject);
    DebugProcess debugProcess = debuggerManager.getDebugProcess(myMainProcessHandler);
    if (debugProcess instanceof DebugProcessImpl) {
      RemoteConnection connection = runConfiguration.createRemoteConnection();
      DebugEnvironment environment = new DefaultDebugEnvironment(myMainExecutionEnvironment, myMainRunnableState, connection, true);
      ApplicationManager.getApplication().invokeAndWait(() -> {
        ((DebugProcessImpl)debugProcess).reattach(environment, true, () -> callback.accept(debugProcess));
      });
    }
  }

  private static void unblockRemote(Socket socket, DataInputStream inputStream) {
    try {
      socket.getOutputStream().write(0);
      inputStream.close();
    }
    catch (IOException e) {
      ExternalSystemTaskDebugRunner.LOG.debug(e);
    }
  }

  private void stopForkedProcessWhenMainProcessTerminated(@Nullable ProcessHandler processHandler) {
    if (processHandler != null) {
      myMainProcessHandler.addProcessListener(new ProcessListener() {
        @Override
        public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
          myMainProcessHandler.removeProcessListener(this);
          terminateForkedProcess(processHandler);
        }
      });
    }
  }

  private void removeRunContentWhenProcessIsTerminated(@NotNull RunContentDescriptor descriptor) {
    ProcessHandler processHandler = descriptor.getProcessHandler();
    if (processHandler != null) {
      processHandler.addProcessListener(new ProcessListener() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
          ToolWindow window = toolWindowManager.getToolWindow(ToolWindowId.DEBUG);
          if (window != null) {
            ContentManager contentManager = window.getContentManager();
            Content content = descriptor.getAttachedContent();
            if (content != null) {
              ApplicationManager.getApplication().invokeLater(() -> contentManager.removeContent(content, true));
            }
          }
        }
      });
    }
  }

  private void initForkedProcessLogger(@NotNull RunContentDescriptor descriptor, @NotNull String processName) {
    ProcessHandler processHandler = descriptor.getProcessHandler();
    if (processHandler != null) {
      processHandler.addProcessListener(new MyForkedProcessListener(descriptor, processName));
    }
  }

  private void initTerminateForkedProcessHandler(@Nullable ProcessHandler processHandler) {
    if (processHandler != null) {
      processHandler.addProcessListener(new ProcessListener() {
        @Override
        public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
          if (!willBeDestroyed) {
            // always terminate forked process
            terminateForkedProcess(event.getProcessHandler());
          }
        }
      });
    }
  }

  private void removeTerminatedForks(@NotNull String processName, @NotNull Socket socket, @NotNull DataInputStream inputStream) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
      ToolWindow window = toolWindowManager.getToolWindow(ToolWindowId.DEBUG);
      if (window != null) {
        ContentManager contentManager = window.getContentManager();
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
      }
      unblockRemote(socket, inputStream);
    });
  }

  private static void runDebugConfiguration(@NotNull RunnerAndConfigurationSettings runSettings, ProgramRunner.Callback callback) {
    try {
      runSettings.setActivateToolWindowBeforeRun(false);
      ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.create(DefaultDebugExecutor.getDebugExecutorInstance(), runSettings)
        .contentToReuse(null)
        .dataContext(null)
        .activeTarget();
      buildWithRuntimeModuleDir(runSettings, builder);
      ExecutionEnvironment environment = builder.build();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        ProgramRunnerUtil.executeConfigurationAsync(environment, true, true, callback);
      });
    }
    catch (ExecutionException e) {
      ExternalSystemTaskDebugRunner.LOG.error(e);
    }
  }

  private static void buildWithRuntimeModuleDir(@NotNull RunnerAndConfigurationSettings runSettings, ExecutionEnvironmentBuilder builder) {
    RunConfiguration configuration = runSettings.getConfiguration();
    if (configuration instanceof UserDataHolder) {
      String moduleDir = ((UserDataHolder)configuration).getUserData(RUNTIME_MODULE_DIR_KEY);
      if (moduleDir != null)
        builder.modulePath(moduleDir);
    }
  }

  private class MyForkedProcessListener implements ProcessListener {
    private final @NotNull RunContentDescriptor myDescriptor;
    private final @NotNull String myProcessName;
    private @Nullable RangeHighlighter myHyperlink;

    MyForkedProcessListener(@NotNull RunContentDescriptor descriptor, @NotNull String processName) {
      myDescriptor = descriptor;
      myProcessName = processName;
    }

    @Override
    public void startNotified(@NotNull ProcessEvent event) {
      postLink();
    }

    @Override
    public void processTerminated(@NotNull ProcessEvent event) {
      removeLink();
    }

    private void postLink() {
      ConsoleViewImpl mainConsoleView = getMainConsoleView();
      if (mainConsoleView != null) {
        ProcessHandler handler = myDescriptor.getProcessHandler();
        String addressDisplayName = "";
        DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(handler);
        if (debugProcess instanceof DebugProcessImpl) {
          addressDisplayName = "(" + JavaDebuggerBundle.getAddressDisplayName(((DebugProcessImpl)debugProcess).getConnection()) + ")";
        }
        String statusText = ExternalSystemBundle.message("debugger.status.connected", myProcessName, addressDisplayName);
        String linkText = ExternalSystemBundle.message("debugger.open.session.tab");
        String debuggerAttachedStatusMessage = statusText + " " + linkText + '\n';
        mainConsoleView.print(debuggerAttachedStatusMessage, ConsoleViewContentType.SYSTEM_OUTPUT);
        mainConsoleView.performWhenNoDeferredOutput(() -> {
          EditorHyperlinkSupport hyperlinkSupport = mainConsoleView.getHyperlinks();
          int messageOffset = mainConsoleView.getText().indexOf(debuggerAttachedStatusMessage);
          int linkStartOffset = messageOffset + debuggerAttachedStatusMessage.indexOf(linkText);
          myHyperlink = hyperlinkSupport.createHyperlink(linkStartOffset, linkStartOffset + linkText.length(), null, project -> {
            // open tab
            final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
            ContentManager contentManager = toolWindowManager.getToolWindow(ToolWindowId.DEBUG).getContentManager();
            Content content = myDescriptor.getAttachedContent();
            if (content != null) {
              contentManager.setSelectedContent(content, true, true);
            }
          });
        });
      }
    }

    private void removeLink() {
      if (myHyperlink != null) {
        ConsoleViewImpl mainConsoleView = getMainConsoleView();
        if (mainConsoleView != null) {
          ApplicationManager.getApplication().invokeLater(
            () -> {
              EditorHyperlinkSupport hyperlinkSupport = mainConsoleView.getHyperlinks();
              int startOffset = myHyperlink.getStartOffset();
              int endOffset = myHyperlink.getEndOffset();
              TextAttributes attributes = myHyperlink.getTextAttributes(mainConsoleView.getEditor().getColorsScheme());
              TextAttributes inactiveTextAttributes = attributes != null ? attributes.clone() : TextAttributes.ERASE_MARKER.clone();
              inactiveTextAttributes.setForegroundColor(NamedColorUtil.getInactiveTextColor());
              inactiveTextAttributes.setEffectColor(NamedColorUtil.getInactiveTextColor());
              inactiveTextAttributes.setFontType(Font.ITALIC);
              hyperlinkSupport.removeHyperlink(myHyperlink);
              hyperlinkSupport.addHighlighter(startOffset, endOffset, inactiveTextAttributes, HighlighterLayer.CONSOLE_FILTER);
            }
          , myProject.getDisposed());
        }
      }
    }

    private @Nullable ConsoleViewImpl getMainConsoleView() {
      ExecutionConsole executionConsole = myMainRunContentDescriptor.getExecutionConsole();
      if (executionConsole instanceof ConsoleViewImpl) {
        return (ConsoleViewImpl)executionConsole;
      }
      if (executionConsole instanceof BuildView buildView) {
        Object consoleView = buildView.getConsoleView();
        if (consoleView instanceof ConsoleViewImpl o) {
          return o;
        }
      }
      return null;
    }
  }

  private void terminateForkedProcess(@NotNull ProcessHandler processHandler) {
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(processHandler);
    if (debugProcess != null) {
      debugProcess.getManagerThread().invokeCommand(new DebuggerCommand() {
        @Override
        public void action() {
          VirtualMachineProxy virtualMachineProxy = debugProcess.getVirtualMachineProxy();
          if (virtualMachineProxy instanceof VirtualMachineProxyImpl &&
              ((VirtualMachineProxyImpl)virtualMachineProxy).canBeModified()) {
            // use success exit code here to avoid the main process interruption
            ((VirtualMachineProxyImpl)virtualMachineProxy).exit(0);
          }
          else {
            debugProcess.stop(true);
          }
        }

        @Override
        public void commandCancelled() {
          debugProcess.stop(true);
        }
      });
    }
    else {
      processHandler.destroyProcess();
    }
  }
}
