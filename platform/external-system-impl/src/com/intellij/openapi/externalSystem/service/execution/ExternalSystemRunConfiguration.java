/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.console.DuplexConsoleView;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * @author Denis Zhdanov
 * @since 23.05.13 18:30
 */
public class ExternalSystemRunConfiguration extends LocatableConfigurationBase implements SearchScopeProvidingRunProfile {
  public static final Key<InputStream> RUN_INPUT_KEY = Key.create("RUN_INPUT_KEY");

  private static final Logger LOG = Logger.getInstance(ExternalSystemRunConfiguration.class);
  private ExternalSystemTaskExecutionSettings mySettings = new ExternalSystemTaskExecutionSettings();

  public ExternalSystemRunConfiguration(@NotNull ProjectSystemId externalSystemId,
                                        Project project,
                                        ConfigurationFactory factory,
                                        String name) {
    super(project, factory, name);
    mySettings.setExternalSystemIdString(externalSystemId.getId());
  }

  @Override
  public String suggestedName() {
    return AbstractExternalSystemTaskConfigurationType.generateName(getProject(), mySettings);
  }

  @Override
  public ExternalSystemRunConfiguration clone() {
    ExternalSystemRunConfiguration result = (ExternalSystemRunConfiguration)super.clone();
    result.mySettings = mySettings.clone();
    return result;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    Element e = element.getChild(ExternalSystemTaskExecutionSettings.TAG_NAME);
    if (e != null) {
      mySettings = XmlSerializer.deserialize(e, ExternalSystemTaskExecutionSettings.class);
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    element.addContent(XmlSerializer.serialize(mySettings, new SerializationFilter() {
      @Override
      public boolean accepts(@NotNull Accessor accessor, @NotNull Object bean) {
        // only these fields due to backward compatibility
        switch (accessor.getName()) {
          case "passParentEnvs":
            return !mySettings.isPassParentEnvs();
          case "env":
            return !mySettings.getEnv().isEmpty();
          default:
            return true;
        }
      }
    }));
  }

  @NotNull
  public ExternalSystemTaskExecutionSettings getSettings() {
    return mySettings;
  }

  @NotNull
  @Override
  public SettingsEditor<ExternalSystemRunConfiguration> getConfigurationEditor() {
    SettingsEditorGroup<ExternalSystemRunConfiguration> group = new SettingsEditorGroup<>();
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new ExternalSystemRunConfigurationEditor(getProject(), mySettings.getExternalSystemId()));
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<>());
    return group;
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    MyRunnableState runnableState =
      new MyRunnableState(mySettings, getProject(), DefaultDebugExecutor.EXECUTOR_ID.equals(executor.getId()), this, env);
    copyUserDataTo(runnableState);
    return runnableState;
  }

  @Nullable
  @Override
  public GlobalSearchScope getSearchScope() {
    GlobalSearchScope scope = null;
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(mySettings.getExternalSystemId());
    if (manager != null) {
      scope = manager.getSearchScope(getProject(), mySettings);
    }
    if (scope == null) {
      VirtualFile file = VfsUtil.findFileByIoFile(new File(mySettings.getExternalProjectPath()), false);
      if (file != null) {
        Module module = DirectoryIndex.getInstance(getProject()).getInfoForFile(file).getModule();
        scope = SearchScopeProvider.createSearchScope(ContainerUtil.ar(module));
      }
    }
    return scope;
  }

  public static class MyRunnableState extends UserDataHolderBase implements RunProfileState {

    @NotNull private final ExternalSystemTaskExecutionSettings mySettings;
    @NotNull private final Project myProject;
    @NotNull private final ExternalSystemRunConfiguration myConfiguration;
    @NotNull private final ExecutionEnvironment myEnv;

    private final int myDebugPort;

    public MyRunnableState(@NotNull ExternalSystemTaskExecutionSettings settings,
                           @NotNull Project project,
                           boolean debug,
                           @NotNull ExternalSystemRunConfiguration configuration,
                           @NotNull ExecutionEnvironment env) {
      mySettings = settings;
      myProject = project;
      myConfiguration = configuration;
      myEnv = env;
      int port;
      if (debug) {
        try {
          port = NetUtils.findAvailableSocketPort();
        }
        catch (IOException e) {
          LOG.warn("Unexpected I/O exception occurred on attempt to find a free port to use for external system task debugging", e);
          port = 0;
        }
      }
      else {
        port = 0;
      }
      myDebugPort = port;
    }

    public int getDebugPort() {
      return myDebugPort;
    }

    @Nullable
    @Override
    public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
      if (myProject.isDisposed()) return null;

      if (mySettings.getTaskNames().isEmpty()) {
        throw new ExecutionException(ExternalSystemBundle.message("run.error.undefined.task"));
      }
      String jvmAgentSetup = null;
      if (myDebugPort > 0) {
        jvmAgentSetup = "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=" + myDebugPort;
      } else {
        ParametersList parametersList = myEnv.getUserData(ExternalSystemTaskExecutionSettings.JVM_AGENT_SETUP_KEY);
        if (parametersList != null) {
          for (String parameter : parametersList.getList()) {
            if (parameter.startsWith("-agentlib:")) continue;
            if (parameter.startsWith("-agentpath:")) continue;
            if (parameter.startsWith("-javaagent:")) continue;
            throw new ExecutionException(ExternalSystemBundle.message("run.invalid.jvm.agent.configuration", parameter));
          }
          jvmAgentSetup = parametersList.getParametersString();
        }
      }

      ApplicationManager.getApplication().assertIsDispatchThread();
      FileDocumentManager.getInstance().saveAllDocuments();

      final ExternalSystemExecuteTaskTask task = new ExternalSystemExecuteTaskTask(myProject, mySettings, jvmAgentSetup);
      copyUserDataTo(task);

      final MyProcessHandler processHandler = new MyProcessHandler(task);
      final ExternalSystemExecutionConsoleManager<ExternalSystemRunConfiguration, ExecutionConsole, ProcessHandler>
        consoleManager = getConsoleManagerFor(task);

      final ExecutionConsole consoleView =
        consoleManager.attachExecutionConsole(task, myProject, myConfiguration, executor, myEnv, processHandler);
      Disposer.register(myProject, consoleView);

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        final String startDateTime = DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis());
        final String greeting;
        if (mySettings.getTaskNames().size() > 1) {
          greeting = ExternalSystemBundle
            .message("run.text.starting.multiple.task", startDateTime, mySettings.toString());
        }
        else {
          greeting =
            ExternalSystemBundle.message("run.text.starting.single.task", startDateTime, mySettings.toString());
        }
        processHandler.notifyTextAvailable(greeting, ProcessOutputTypes.SYSTEM);
        foldGreetingOrFarewell(consoleView, greeting, true);
        ExternalSystemTaskNotificationListenerAdapter taskListener = new ExternalSystemTaskNotificationListenerAdapter() {

          private boolean myResetGreeting = true;

          @Override
          public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
            if (myResetGreeting) {
              processHandler.notifyTextAvailable("\r", ProcessOutputTypes.SYSTEM);
              myResetGreeting = false;
            }

            consoleManager.onOutput(consoleView, processHandler, text, stdOut ? ProcessOutputTypes.STDOUT : ProcessOutputTypes.STDERR);
          }

          @Override
          public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
            String exceptionMessage = ExceptionUtil.getMessage(e);
            String text = exceptionMessage == null ? e.toString() : exceptionMessage;
            processHandler.notifyTextAvailable(text + '\n', ProcessOutputTypes.STDERR);
            processHandler.notifyProcessTerminated(1);
          }

          @Override
          public void onEnd(@NotNull ExternalSystemTaskId id) {
            final String endDateTime = DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis());
            final String farewell;
            if (mySettings.getTaskNames().size() > 1) {
              farewell = ExternalSystemBundle
                .message("run.text.ended.multiple.task", endDateTime, mySettings.toString());
            }
            else {
              farewell =
                ExternalSystemBundle.message("run.text.ended.single.task", endDateTime, mySettings.toString());
            }
            processHandler.notifyTextAvailable(farewell, ProcessOutputTypes.SYSTEM);
            foldGreetingOrFarewell(consoleView, farewell, false);
            processHandler.notifyProcessTerminated(0);
          }
        };
        task.execute(ArrayUtil.prepend(taskListener, ExternalSystemTaskNotificationListener.EP_NAME.getExtensions()));
      });
      DefaultExecutionResult result = new DefaultExecutionResult(consoleView, processHandler);
      result.setRestartActions(consoleManager.getRestartActions(consoleView));
      return result;
    }

    private static void foldGreetingOrFarewell(ExecutionConsole consoleView, String text, boolean isGreeting) {
      int limit = 100;
      if (text.length() < limit) {
        return;
      }
      final ConsoleViewImpl consoleViewImpl;
      if (consoleView instanceof ConsoleViewImpl) {
        consoleViewImpl = (ConsoleViewImpl)consoleView;
      }
      else if (consoleView instanceof DuplexConsoleView) {
        DuplexConsoleView duplexConsoleView = (DuplexConsoleView)consoleView;
        if (duplexConsoleView.getPrimaryConsoleView() instanceof ConsoleViewImpl) {
          consoleViewImpl = (ConsoleViewImpl)duplexConsoleView.getPrimaryConsoleView();
        }
        else if (duplexConsoleView.getSecondaryConsoleView() instanceof ConsoleViewImpl) {
          consoleViewImpl = (ConsoleViewImpl)duplexConsoleView.getSecondaryConsoleView();
        }
        else {
          consoleViewImpl = null;
        }
      }
      else {
        consoleViewImpl = null;
      }
      if (consoleViewImpl != null) {
        consoleViewImpl.performWhenNoDeferredOutput(() -> {
          if(!ApplicationManager.getApplication().isDispatchThread()) return;

          Document document = consoleViewImpl.getEditor().getDocument();
          int line = isGreeting ? 0 : document.getLineCount() - 2;
          if (CharArrayUtil.regionMatches(document.getCharsSequence(), document.getLineStartOffset(line), text)) {
            final FoldingModel foldingModel = consoleViewImpl.getEditor().getFoldingModel();
            foldingModel.runBatchFoldingOperation(() -> {
              FoldRegion region = foldingModel.addFoldRegion(document.getLineStartOffset(line),
                                                             document.getLineEndOffset(line),
                                                             StringUtil.trimLog(text, limit));
              if (region != null) {
                region.setExpanded(false);
              }
            });
          }
        });
      }
    }
  }

  private static class MyProcessHandler extends ProcessHandler implements AnsiEscapeDecoder.ColoredTextAcceptor {
    private final ExternalSystemExecuteTaskTask myTask;
    private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();
    @Nullable
    private OutputStream myProcessInput;

    public MyProcessHandler(ExternalSystemExecuteTaskTask task) {
      myTask = task;
      try {
        PipedInputStream inputStream = new PipedInputStream();
        myProcessInput = new PipedOutputStream(inputStream);
        task.putUserData(RUN_INPUT_KEY, inputStream);
      }
      catch (IOException e) {
        LOG.warn("Unable to setup process input", e);
      }
    }

    @Override
    public void notifyTextAvailable(final String text, final Key outputType) {
      myAnsiEscapeDecoder.escapeText(text, outputType, this);
    }

    @Override
    protected void destroyProcessImpl() {
      myTask.cancel();
      closeInput();
    }

    @Override
    protected void detachProcessImpl() {
      notifyProcessDetached();
      closeInput();
    }

    @Override
    public boolean detachIsDefault() {
      return false;
    }

    @Nullable
    @Override
    public OutputStream getProcessInput() {
      return myProcessInput;
    }

    @Override
    public void notifyProcessTerminated(int exitCode) {
      super.notifyProcessTerminated(exitCode);
      closeInput();
    }

    @Override
    public void coloredTextAvailable(@NotNull String text, @NotNull Key attributes) {
      super.notifyTextAvailable(text, attributes);
    }

    private void closeInput() {
      StreamUtil.closeStream(myProcessInput);
      myProcessInput = null;
    }
  }

  @NotNull
  private static ExternalSystemExecutionConsoleManager<ExternalSystemRunConfiguration, ExecutionConsole, ProcessHandler>
  getConsoleManagerFor(@NotNull ExternalSystemTask task) {
    for (ExternalSystemExecutionConsoleManager executionConsoleManager : ExternalSystemExecutionConsoleManager.EP_NAME.getExtensions()) {
      if (executionConsoleManager.isApplicableFor(task))
        //noinspection unchecked
        return executionConsoleManager;
    }

    return new DefaultExternalSystemExecutionConsoleManager();
  }

}
