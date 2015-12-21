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
package com.intellij.debugger;

import com.intellij.JavaTestUtil;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.RemoteStateState;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.sun.jdi.Location;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.StringTokenizer;

public abstract class DebuggerTestCase extends ExecutionWithDebuggerToolsTestCase {
  protected DebuggerSession myDebuggerSession;

  @Override
  protected void initApplication() throws Exception {
    super.initApplication();
    JavaTestUtil.setupTestJDK();
    DebuggerSettings.getInstance().DEBUGGER_TRANSPORT = DebuggerSettings.SOCKET_TRANSPORT;
    DebuggerSettings.getInstance().SKIP_CONSTRUCTORS = false;
    DebuggerSettings.getInstance().SKIP_GETTERS      = false;
    NodeRendererSettings.getInstance().getClassRenderer().SHOW_DECLARED_TYPE = true;
  }

  @Override
  protected void runTest() throws Throwable {
    super.runTest();
    if(getDebugProcess() != null) {
      getDebugProcess().getProcessHandler().startNotify();
      waitProcess(getDebugProcess().getProcessHandler());
      waitForCompleted();
      //disposeSession(myDebuggerSession);
      assertNull(DebuggerManagerEx.getInstanceEx(myProject).getDebugProcess(getDebugProcess().getProcessHandler()));
      myDebuggerSession = null;
    }
    throwExceptionsIfAny();
    checkTestOutput();
  }

  protected void checkTestOutput() throws Exception {
    getChecker().checkValid(getTestProjectJdk());
  }

  protected void disposeSession(final DebuggerSession debuggerSession) throws InterruptedException, InvocationTargetException {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        debuggerSession.dispose();
      }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      FileEditorManagerEx.getInstanceEx(getProject()).closeAllFiles();
      if (myDebugProcess != null) {
        myDebugProcess.stop(true);
        myDebugProcess.waitFor();
      }
    }
    finally {
      super.tearDown();
    }
  }

  protected void createLocalProcess(String className) throws ExecutionException, InterruptedException, InvocationTargetException {
    LOG.assertTrue(myDebugProcess == null);
    myDebuggerSession = createLocalProcess(DebuggerSettings.SOCKET_TRANSPORT, createJavaParameters(className));
    myDebugProcess = myDebuggerSession.getProcess();
  }

  protected DebuggerSession createLocalSession(final JavaParameters javaParameters) throws ExecutionException, InterruptedException {
    createBreakpoints(javaParameters.getMainClass());
    DebuggerSettings.getInstance().DEBUGGER_TRANSPORT = DebuggerSettings.SOCKET_TRANSPORT;

    GenericDebuggerRunnerSettings debuggerRunnerSettings = new GenericDebuggerRunnerSettings();
    debuggerRunnerSettings.LOCAL = true;

    final RemoteConnection debugParameters = DebuggerManagerImpl.createDebugParameters(javaParameters, debuggerRunnerSettings, false);

    ExecutionEnvironment environment = new ExecutionEnvironmentBuilder(myProject, DefaultDebugExecutor.getDebugExecutorInstance())
      .runnerSettings(debuggerRunnerSettings)
      .runProfile(new MockConfiguration())
      .build();
    final JavaCommandLineState javaCommandLineState = new JavaCommandLineState(environment){
      @Override
      protected JavaParameters createJavaParameters() {
        return javaParameters;
      }

      @Override
      protected GeneralCommandLine createCommandLine() throws ExecutionException {
        return CommandLineBuilder.createFromJavaParameters(getJavaParameters());
      }
    };

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          myDebuggerSession =
            DebuggerManagerEx.getInstanceEx(myProject)
              .attachVirtualMachine(new DefaultDebugEnvironment(new ExecutionEnvironmentBuilder(myProject, DefaultDebugExecutor.getDebugExecutorInstance())
                                                                  .runProfile(new MockConfiguration())
                                                                  .build(), javaCommandLineState, debugParameters, false));
          XDebuggerManager.getInstance(myProject).startSession(javaCommandLineState.getEnvironment(), new XDebugProcessStarter() {
            @Override
            @NotNull
            public XDebugProcess start(@NotNull XDebugSession session) {
              return JavaDebugProcess.create(session, myDebuggerSession);
            }
          });
        }
        catch (ExecutionException e) {
          LOG.error(e);
        }
      }
    }, ModalityState.defaultModalityState());
    myDebugProcess = myDebuggerSession.getProcess();

    myDebugProcess.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        print(event.getText(), outputType);
      }
    });

    assertNotNull(myDebuggerSession);
    assertNotNull(myDebugProcess);

    return myDebuggerSession;
  }


  protected DebuggerSession createLocalProcess(int transport, final JavaParameters javaParameters) throws ExecutionException, InterruptedException, InvocationTargetException {
    createBreakpoints(javaParameters.getMainClass());
    final DebuggerSession[] debuggerSession = new DebuggerSession[]{null};

    DebuggerSettings.getInstance().DEBUGGER_TRANSPORT = transport;

    GenericDebuggerRunnerSettings debuggerRunnerSettings = new GenericDebuggerRunnerSettings();
    debuggerRunnerSettings.LOCAL = true;
    debuggerRunnerSettings.setDebugPort("3456");

    ExecutionEnvironment environment = new ExecutionEnvironmentBuilder(myProject, DefaultDebugExecutor.getDebugExecutorInstance())
      .runnerSettings(debuggerRunnerSettings)
      .runProfile(new MockConfiguration())
      .build();
    final JavaCommandLineState javaCommandLineState = new JavaCommandLineState(environment) {
      @Override
      protected JavaParameters createJavaParameters() {
        return javaParameters;
      }

      @Override
      protected GeneralCommandLine createCommandLine() throws ExecutionException {
        return CommandLineBuilder.createFromJavaParameters(getJavaParameters());
      }
    };

    final RemoteConnection debugParameters =
      DebuggerManagerImpl.createDebugParameters(javaCommandLineState.getJavaParameters(), debuggerRunnerSettings, true);

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          debuggerSession[0] = attachVirtualMachine(javaCommandLineState, javaCommandLineState.getEnvironment(), debugParameters, false);
        }
        catch (ExecutionException e) {
          fail(e.getMessage());
        }
      }
    });

    final ProcessHandler processHandler = debuggerSession[0].getProcess().getProcessHandler();
    debuggerSession[0].getProcess().addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        print(event.getText(), outputType);
      }
    });

    DebugProcessImpl process =
      (DebugProcessImpl)DebuggerManagerEx.getInstanceEx(myProject).getDebugProcess(processHandler);
    assertNotNull(process);
    return debuggerSession[0];
  }


  protected DebuggerSession createRemoteProcess(final int transport, final boolean serverMode, JavaParameters javaParameters)
          throws ExecutionException, InterruptedException, InvocationTargetException {
    boolean useSockets = transport == DebuggerSettings.SOCKET_TRANSPORT;

    RemoteConnection remoteConnection = new RemoteConnection(
      useSockets,
      "127.0.0.1",
      "3456",
      serverMode);

    String launchCommandLine = remoteConnection.getLaunchCommandLine();

    launchCommandLine = StringUtil.replace(launchCommandLine,  RemoteConnection.ONTHROW, "");
    launchCommandLine = StringUtil.replace(launchCommandLine,  RemoteConnection.ONUNCAUGHT, "");

    launchCommandLine = StringUtil.replace(launchCommandLine, "suspend=n", "suspend=y");

    println(launchCommandLine, ProcessOutputTypes.SYSTEM);

    for(StringTokenizer tokenizer = new StringTokenizer(launchCommandLine);tokenizer.hasMoreTokens();) {
      String token = tokenizer.nextToken();
      javaParameters.getVMParametersList().add(token);
    }

    GeneralCommandLine commandLine = CommandLineBuilder.createFromJavaParameters(javaParameters);


    DebuggerSession debuggerSession;

    if(serverMode) {
      debuggerSession = attachVM(remoteConnection, false);
      commandLine.createProcess();
    } else {
      commandLine.createProcess();
      debuggerSession = attachVM(remoteConnection, true);
    }

    ProcessHandler processHandler = debuggerSession.getProcess().getProcessHandler();
    DebugProcessImpl process = (DebugProcessImpl)DebuggerManagerEx.getInstanceEx(myProject)
      .getDebugProcess(processHandler);

    assertNotNull(process);
    return debuggerSession;
  }

  protected DebuggerSession attachVM(final RemoteConnection remoteConnection, final boolean pollConnection)
          throws InvocationTargetException, InterruptedException {
    final RemoteState remoteState = new RemoteStateState(myProject, remoteConnection);

    final DebuggerSession[] debuggerSession = new DebuggerSession[1];
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          debuggerSession[0] = attachVirtualMachine(remoteState, new ExecutionEnvironmentBuilder(myProject, DefaultDebugExecutor.getDebugExecutorInstance())
            .runProfile(new MockConfiguration())
            .build(), remoteConnection, pollConnection);
        }
        catch (ExecutionException e) {
          fail(e.getMessage());
        }
      }
    });
    debuggerSession[0].getProcess().getProcessHandler().addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        print(event.getText(), outputType);
      }
    });
    return debuggerSession[0];
  }

  protected void createBreakpoints(final String className) {
    final PsiFile psiFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      @Override
      public PsiFile compute() {
        PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
        assertNotNull(psiClass);
        return psiClass.getContainingFile();
      }
    });

    createBreakpoints(psiFile);
  }

  protected EvaluationContextImpl createEvaluationContext(final SuspendContextImpl suspendContext) {
    try {
      StackFrameProxyImpl proxy = suspendContext.getFrameProxy();
      assertNotNull(proxy);
      return new EvaluationContextImpl(suspendContext, proxy, proxy.thisObject());
    }
    catch (EvaluateException e) {
      error(e);
      return null;
    }
  }

  protected void waitForCompleted() {
    final SynchronizationBasedSemaphore s = new SynchronizationBasedSemaphore();
    s.down();

    final InvokeThread.WorkerThreadRequest request = getDebugProcess().getManagerThread().getCurrentRequest();
    final Thread thread = new Thread("Joining "+request) {
      @Override
      public void run() {
        try {
          request.join();
        }
        catch (Exception ignored) {
        }
      }
    };
    thread.start();
    if(request.isDone()) {
      thread.interrupt();
    }
      waitFor(new Runnable() {
        @Override
        public void run() {
          try {
            thread.join();
          }
          catch (InterruptedException ignored) {
          }
        }
      });

    invokeRatherLater(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        LOG.assertTrue(false);
      }

      @Override
      protected void commandCancelled() {
        //We wait for invokeRatherLater's
        invokeRatherLater(new DebuggerCommandImpl() {
          @Override
          protected void action() throws Exception {
            LOG.assertTrue(false);
          }

          @Override
          protected void commandCancelled() {
            s.up();
          }
        });
      }
    });

    waitFor(new Runnable() {
      @Override
      public void run() {
        s.waitFor();
      }
    });
  }

  public DebuggerContextImpl createDebuggerContext(final SuspendContextImpl suspendContext, StackFrameProxyImpl stackFrame) {
    final DebuggerSession[] session = new DebuggerSession[1];

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        session[0] = DebuggerManagerEx.getInstanceEx(myProject).getSession(suspendContext.getDebugProcess());
      }
    });

    DebuggerContextImpl debuggerContext = DebuggerContextImpl.createDebuggerContext(
            session[0],
            suspendContext,
            stackFrame != null ? stackFrame.threadProxy() : null,
            stackFrame);
    debuggerContext.initCaches();
    return debuggerContext;
  }

  public DebuggerContextImpl createDebuggerContext(final SuspendContextImpl suspendContext) {
    return createDebuggerContext(suspendContext, suspendContext.getFrameProxy());
  }

  protected void printLocation(SuspendContextImpl suspendContext) {
    try {
      Location location = suspendContext.getFrameProxy().location();
      String message = "paused at " + location.sourceName() + ":" + location.lineNumber();
      println(message, ProcessOutputTypes.SYSTEM);
    }
    catch (Throwable e) {
      addException(e);
    }
  }

  protected void createBreakpointInHelloWorld() {
    DebuggerInvocationUtil.invokeAndWait(myProject, new Runnable() {
      @Override
      public void run() {
        BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
        PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass("HelloWorld", GlobalSearchScope.allScope(myProject));
        assertNotNull(psiClass);
        Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiClass.getContainingFile());
        breakpointManager.addLineBreakpoint(document, 3);
      }
    }, ApplicationManager.getApplication().getDefaultModalityState());
  }

  protected void createHelloWorldProcessWithBreakpoint() throws ExecutionException, InterruptedException, InvocationTargetException {
    createLocalProcess("HelloWorld");

    createBreakpointInHelloWorld();
  }

  @Override
  protected DebugProcessImpl getDebugProcess() {
    return myDebuggerSession != null ? myDebuggerSession.getProcess() : null;
  }

  public DebuggerSession getDebuggerSession() {
    return myDebuggerSession;
  }

  protected DebuggerSession attachVirtualMachine(RunProfileState state,
                                                 ExecutionEnvironment environment,
                                                 RemoteConnection remoteConnection,
                                                 boolean pollConnection) throws ExecutionException {
    final DebuggerSession debuggerSession =
      DebuggerManagerEx.getInstanceEx(myProject).attachVirtualMachine(new DefaultDebugEnvironment(environment, state, remoteConnection, pollConnection));
    XDebuggerManager.getInstance(myProject).startSession(environment, new XDebugProcessStarter() {
      @Override
      @NotNull
      public XDebugProcess start(@NotNull XDebugSession session) {
        return JavaDebugProcess.create(session, debuggerSession);
      }
    });
    return debuggerSession;
  }

  public class MockConfiguration implements ModuleRunConfiguration {
    @Override
    @NotNull
    public Module[] getModules() {
      if (myModule != null) {
        return new Module[]{myModule};
      }
      else {
        return Module.EMPTY_ARRAY;
      }
    }

    @Override
    public Icon getIcon() {
      return null;
    }

    @Override
    public ConfigurationFactory getFactory() {
      return null;
    }

    @Override
    public void setName(String name) { }

    @NotNull
    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Project getProject() {
      return null;
    }

    @Override
    @NotNull
    public ConfigurationType getType() {
      return UnknownConfigurationType.INSTANCE;
    }

    @Override
    public ConfigurationPerRunnerSettings createRunnerSettings(ConfigurationInfoProvider provider) {
      return null;
    }

    @Override
    public SettingsEditor<ConfigurationPerRunnerSettings> getRunnerSettingsEditor(ProgramRunner runner) {
      return null;
    }

    @Override
    public RunConfiguration clone() {
      return null;
    }

    @Override
    public int getUniqueID() {
      return 0;
    }

    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
      return null;
    }

    @Override
    public String getName() {
      return "";
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException { }

    @Override
    public void readExternal(Element element) throws InvalidDataException { }

    @Override
    public void writeExternal(Element element) throws WriteExternalException { }
  }
}
