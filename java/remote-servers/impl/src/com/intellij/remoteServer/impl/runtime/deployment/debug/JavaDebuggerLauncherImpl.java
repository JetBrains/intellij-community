package com.intellij.remoteServer.impl.runtime.deployment.debug;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebugUIEnvironment;
import com.intellij.debugger.engine.RemoteDebugProcessHandler;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.execution.*;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.runtime.deployment.debug.JavaDebugConnectionData;
import com.intellij.remoteServer.runtime.deployment.debug.JavaDebuggerLauncher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class JavaDebuggerLauncherImpl extends JavaDebuggerLauncher {
  private static final Logger LOG = Logger.getInstance(JavaDebuggerLauncherImpl.class);

  @Override
  public void startDebugSession(@NotNull JavaDebugConnectionData info, @NotNull ExecutionEnvironment executionEnvironment, @NotNull RemoteServer<?> server)
    throws ExecutionException {
    final Project project = executionEnvironment.getProject();
    Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
    final DebuggerPanelsManager manager = DebuggerPanelsManager.getInstance(project);
    final RemoteConnection remoteConnection = new RemoteConnection(true, info.getHost(), String.valueOf(info.getPort()), false);
    DebugEnvironment debugEnvironment = new RemoteServerDebugEnvironment(project, remoteConnection, executionEnvironment.getRunProfile());
    DebugUIEnvironment debugUIEnvironment = new RemoteServerDebugUIEnvironment(debugEnvironment, executionEnvironment);
    RunContentDescriptor debugContentDescriptor = manager.attachVirtualMachine(debugUIEnvironment);
    LOG.assertTrue(debugContentDescriptor != null);
    ProcessHandler processHandler = debugContentDescriptor.getProcessHandler();
    LOG.assertTrue(processHandler != null);
    processHandler.startNotify();
    ExecutionManager.getInstance(project).getContentManager().showRunContent(executor, debugContentDescriptor,
                                                                             executionEnvironment.getContentToReuse());
  }

  private static class RemoteServerDebugUIEnvironment implements DebugUIEnvironment {
    private final DebugEnvironment myEnvironment;
    private final ExecutionEnvironment myExecutionEnvironment;

    public RemoteServerDebugUIEnvironment(DebugEnvironment environment, ExecutionEnvironment executionEnvironment) {
      myEnvironment = environment;
      myExecutionEnvironment = executionEnvironment;
    }

    @Override
    public DebugEnvironment getEnvironment() {
      return myEnvironment;
    }

    @Nullable
    @Override
    public RunContentDescriptor getReuseContent() {
      return myExecutionEnvironment.getContentToReuse();
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myExecutionEnvironment.getRunProfile().getIcon();
    }

    @Override
    public void initLogs(RunContentDescriptor content, LogFilesManager logFilesManager) {
    }

    @Override
    public void initActions(RunContentDescriptor content, DefaultActionGroup actionGroup) {
      actionGroup.add(new CloseAction(myExecutionEnvironment.getExecutor(), content, myExecutionEnvironment.getProject()));
    }

    @Nullable
    @Override
    public RunProfile getRunProfile() {
      return myExecutionEnvironment.getRunProfile();
    }
  }

  private static class RemoteServerDebugEnvironment implements DebugEnvironment {
    private final Project myProject;
    private final GlobalSearchScope mySearchScope;
    private final RemoteConnection myRemoteConnection;
    private final RunProfile myRunProfile;

    public RemoteServerDebugEnvironment(Project project, RemoteConnection remoteConnection, RunProfile runProfile) {
      myProject = project;
      mySearchScope = RunContentBuilder.createSearchScope(project, runProfile);
      myRemoteConnection = remoteConnection;
      myRunProfile = runProfile;
    }

    @Nullable
    @Override
    public ExecutionResult createExecutionResult() throws ExecutionException {
      ConsoleViewImpl consoleView = new ConsoleViewImpl(myProject, false);
      RemoteDebugProcessHandler process = new RemoteDebugProcessHandler(myProject);
      consoleView.attachToProcess(process);
      return new DefaultExecutionResult(consoleView, process);
    }

    @Override
    public GlobalSearchScope getSearchScope() {
      return mySearchScope;
    }

    @Override
    public boolean isRemote() {
      return true;
    }

    @Override
    public RemoteConnection getRemoteConnection() {
      return myRemoteConnection;
    }

    @Override
    public boolean isPollConnection() {
      return true;
    }

    @Override
    public String getSessionName() {
      return myRunProfile.getName();
    }
  }
}
