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
package com.intellij.debugger;

import com.intellij.debugger.engine.DebugProcessImpl;
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
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.StringTokenizer;

public abstract class DebuggerTestCase extends ExecutionWithDebuggerToolsTestCase {
  protected DebuggerSession myDebuggerSession;
  private StringBuffer myConsoleBuffer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected void initApplication() throws Exception {
    super.initApplication();
    setTestJDK();
    DebuggerSettings.getInstance().DEBUGGER_TRANSPORT = DebuggerSettings.SOCKET_TRANSPORT;
    DebuggerSettings.getInstance().SKIP_CONSTRUCTORS = false;
    DebuggerSettings.getInstance().SKIP_GETTERS      = false;
    NodeRendererSettings.getInstance().getClassRenderer().SHOW_DECLARED_TYPE = true;
  }

  @Override
  protected void runTest() throws Throwable {
    super.runTest();
    if(getDebugProcess() != null) {
      getDebugProcess().getExecutionResult().getProcessHandler().startNotify();
      waitProcess(getDebugProcess().getExecutionResult().getProcessHandler());
      waitForCompleted();
      disposeSession(myDebuggerSession);
      assertNull(DebuggerManagerEx.getInstanceEx(myProject).getDebugProcess(getDebugProcess().getExecutionResult().getProcessHandler()));
      myDebuggerSession = null;
    }
    if(myConsoleBuffer != null) {
      //println("", b);
      //println("Console output:", b);
      //println(myConsoleBuffer.toString(), b);
    }
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
    super.tearDown();
    myConsoleBuffer = null;
  }

  protected void createLocalProcess(String className) throws ExecutionException, InterruptedException, InvocationTargetException {
    LOG.assertTrue(myDebugProcess == null);
    myDebuggerSession = createLocalProcess(DebuggerSettings.SOCKET_TRANSPORT, createJavaParameters(className));
    myDebugProcess = myDebuggerSession.getProcess();
  }

  protected DebuggerSession createLocalSession(final JavaParameters javaParameters, final String sessionName) throws ExecutionException, InterruptedException {
    createBreakpoints(javaParameters.getMainClass());
    DebuggerSettings.getInstance().DEBUGGER_TRANSPORT = DebuggerSettings.SOCKET_TRANSPORT;

    GenericDebuggerRunnerSettings debuggerRunnerSettings = new GenericDebuggerRunnerSettings();
    debuggerRunnerSettings.LOCAL      = true;

    final RemoteConnection debugParameters = DebuggerManagerImpl.createDebugParameters(javaParameters, debuggerRunnerSettings, false);

    ExecutionEnvironment environment = new ExecutionEnvironment(new MockConfiguration(), DefaultDebugExecutor.getDebugExecutorInstance(),
                                                                myProject, debuggerRunnerSettings);
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

    final GenericDebuggerRunner runner = new GenericDebuggerRunner();

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          myDebuggerSession = DebuggerManagerEx.getInstanceEx(myProject).attachVirtualMachine(DefaultDebugExecutor.getDebugExecutorInstance(),
            runner, new MockConfiguration(), javaCommandLineState, debugParameters, false);
        }
        catch (ExecutionException e) {
          LOG.error(e);
        }
      }
    }, ModalityState.defaultModalityState());
    myDebugProcess = myDebuggerSession.getProcess();

    //myConsoleBuffer = new StringBuffer();

    myDebugProcess.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        //myConsoleBuffer.append(event.getText());
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
    debuggerRunnerSettings.DEBUG_PORT = "3456";

    ExecutionEnvironment environment = new ExecutionEnvironment(new MockConfiguration(), DefaultDebugExecutor.getDebugExecutorInstance(), myProject,
                                                                debuggerRunnerSettings);
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
          debuggerSession[0] = attachVirtualMachine(javaCommandLineState, debugParameters, false);
        }
        catch (ExecutionException e) {
          fail(e.getMessage());
        }
      }
    });

    final ExecutionResult executionResult = debuggerSession[0].getProcess().getExecutionResult();
    debuggerSession[0].getProcess().addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        print(event.getText(), outputType);
      }
    });

    DebugProcessImpl process =
      (DebugProcessImpl)DebuggerManagerEx.getInstanceEx(myProject).getDebugProcess(executionResult.getProcessHandler());
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

    JavaParameters parameters = javaParameters;

    for(StringTokenizer tokenizer = new StringTokenizer(launchCommandLine);tokenizer.hasMoreTokens();) {
      String token = tokenizer.nextToken();
      parameters.getVMParametersList().add(token);
    }

    GeneralCommandLine commandLine = CommandLineBuilder.createFromJavaParameters(parameters);


    DebuggerSession debuggerSession;

    if(serverMode) {
      debuggerSession = attachVM(remoteConnection, false);
      commandLine.createProcess();
    } else {
      commandLine.createProcess();
      debuggerSession = attachVM(remoteConnection, true);
    }

    ExecutionResult executionResult = debuggerSession.getProcess().getExecutionResult();
    DebugProcessImpl process = (DebugProcessImpl)DebuggerManagerEx.getInstanceEx(myProject)
      .getDebugProcess(executionResult.getProcessHandler());

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
          debuggerSession[0] = attachVirtualMachine(remoteState, remoteConnection, pollConnection);
        }
        catch (ExecutionException e) {
          fail(e.getMessage());
        }
      }
    });
    debuggerSession[0].getProcess().getExecutionResult().getProcessHandler().addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        print(event.getText(), outputType);
      }
    });
    return debuggerSession[0];
  }

  protected void createBreakpoints(final String className) {
    final PsiClass psiClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Override
      public PsiClass compute() {
        return JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
      }
    });

    createBreakpoints(psiClass.getContainingFile());

  }

  protected EvaluationContextImpl createEvaluationContext(final SuspendContextImpl suspendContext) {
    try {
      return new EvaluationContextImpl(
        suspendContext,
        suspendContext.getFrameProxy(),
        suspendContext.getFrameProxy().thisObject());
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
        catch (Exception e) {
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
          catch (InterruptedException e) {
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

  protected void createBreakpointInHelloWorld() {
    DebuggerInvocationUtil.invokeAndWait(myProject, new Runnable() {
      @Override
      public void run() {
        BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager();
        PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass("HelloWorld", GlobalSearchScope.allScope(myProject));
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

  protected DebuggerSession attachVirtualMachine(RunProfileState state, RemoteConnection remoteConnection, boolean pollConnection) throws ExecutionException {
    return DebuggerManagerEx.getInstanceEx(myProject).attachVirtualMachine(DefaultDebugExecutor.getDebugExecutorInstance(),
                                                                           new GenericDebuggerRunner(),
                                                                           new MockConfiguration(), state, remoteConnection, pollConnection);
  }

  private static class MockConfiguration implements ModuleRunConfiguration {
    @Override
    @NotNull
    public Module[] getModules() {
      return Module.EMPTY_ARRAY;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Icon getIcon() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public ConfigurationFactory getFactory() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void setName(final String name) {
      //To change body of implemented methods use File | Settings | File Templates.
    }

    @NotNull
    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Project getProject() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    @NotNull
    public ConfigurationType getType() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public ConfigurationPerRunnerSettings createRunnerSettings(final ConfigurationInfoProvider provider) {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public SettingsEditor<ConfigurationPerRunnerSettings> getRunnerSettingsEditor(final ProgramRunner runner) {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public RunConfiguration clone() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public int getUniqueID() {
      return 0;
    }

    @Override
    public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) throws ExecutionException {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getName() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
      //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void readExternal(final Element element) throws InvalidDataException {
      //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void writeExternal(final Element element) throws WriteExternalException {
      //To change body of implemented methods use File | Settings | File Templates.
    }
  }

}
