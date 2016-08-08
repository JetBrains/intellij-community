package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.net.NetUtils;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 23.05.13 18:30
 */
public class ExternalSystemRunConfiguration extends LocatableConfigurationBase {

  private static final Logger LOG = Logger.getInstance("#" + ExternalSystemRunConfiguration.class.getName());

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
    element.addContent(XmlSerializer.serialize(mySettings));
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
    return new MyRunnableState(mySettings, getProject(), DefaultDebugExecutor.EXECUTOR_ID.equals(executor.getId()), this, env);
  }

  public static class MyRunnableState implements RunProfileState {

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

      final List<ExternalTaskPojo> tasks = ContainerUtilRt.newArrayList();
      for (String taskName : mySettings.getTaskNames()) {
        tasks.add(new ExternalTaskPojo(taskName, mySettings.getExternalProjectPath(), null));
      }
      if (tasks.isEmpty()) {
        throw new ExecutionException(ExternalSystemBundle.message("run.error.undefined.task"));
      }
      String debuggerSetup = null;
      if (myDebugPort > 0) {
        debuggerSetup = "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=" + myDebugPort;
      }

      ApplicationManager.getApplication().assertIsDispatchThread();
      FileDocumentManager.getInstance().saveAllDocuments();

      final ExternalSystemExecuteTaskTask task = new ExternalSystemExecuteTaskTask(mySettings.getExternalSystemId(),
                                                                                   myProject,
                                                                                   tasks,
                                                                                   mySettings.getVmOptions(),
                                                                                   mySettings.getScriptParameters(),
                                                                                   debuggerSetup);

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
        task.execute(new ExternalSystemTaskNotificationListenerAdapter() {

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
            processHandler.notifyProcessTerminated(0);
          }
        });
      });
      DefaultExecutionResult result = new DefaultExecutionResult(consoleView, processHandler);
      result.setRestartActions(consoleManager.getRestartActions(consoleView));
      return result;
    }
  }

  private static class MyProcessHandler extends ProcessHandler implements AnsiEscapeDecoder.ColoredTextAcceptor {
    private final ExternalSystemExecuteTaskTask myTask;
    private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();

    public MyProcessHandler(ExternalSystemExecuteTaskTask task) {
      myTask = task;
    }

    @Override
    public void notifyTextAvailable(final String text, final Key outputType) {
      myAnsiEscapeDecoder.escapeText(text, outputType, this);
    }

    @Override
    protected void destroyProcessImpl() {
    }

    @Override
    protected void detachProcessImpl() {
      myTask.cancel();
      notifyProcessDetached();
    }

    @Override
    public boolean detachIsDefault() {
      return true;
    }

    @Nullable
    @Override
    public OutputStream getProcessInput() {
      return null;
    }

    @Override
    public void notifyProcessTerminated(int exitCode) {
      super.notifyProcessTerminated(exitCode);
    }

    @Override
    public void coloredTextAvailable(String text, Key attributes) {
      super.notifyTextAvailable(text, attributes);
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
