package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.serialization.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 23.05.13 18:30
 */
public class ExternalSystemRunConfiguration extends RunConfigurationBase {
  
  private ExternalSystemTaskExecutionSettings mySettings = new ExternalSystemTaskExecutionSettings();

  public ExternalSystemRunConfiguration(@NotNull ProjectSystemId externalSystemId,
                                        Project project,
                                        ConfigurationFactory factory,
                                        String name)
  {
    super(project, factory, name);
    mySettings.setExternalSystemIdString(externalSystemId.getId());
  }

  @Override
  public RunConfiguration clone() {
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

  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new ExternalSystemRunConfigurationEditor(getProject(), mySettings.getExternalSystemId());
  }

  @Nullable
  @Override
  public JDOMExternalizable createRunnerSettings(ConfigurationInfoProvider provider) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Nullable
  @Override
  public SettingsEditor<JDOMExternalizable> getRunnerSettingsEditor(ProgramRunner runner) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    return new RunnableState() {
      @Nullable
      @Override
      public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
        ConsoleView console = new TextConsoleBuilderImpl(getProject()).getConsole();
        final MyProcessHandler processHandler = new MyProcessHandler();
        console.attachToProcess(processHandler);
        final List<ExternalTaskPojo> tasks = ContainerUtilRt.newArrayList();
        for (String taskName : mySettings.getTaskNames()) {
          tasks.add(new ExternalTaskPojo(taskName, mySettings.getExternalProjectPath(), null));
        }
        final ExternalSystemExecuteTaskTask task = new ExternalSystemExecuteTaskTask(mySettings.getExternalSystemId(),
                                                                                     getProject(),
                                                                                     tasks,
                                                                                     mySettings.getVmOptions());
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            String greeting = ExternalSystemBundle.message("run.text.starting", StringUtil.join(mySettings.getTaskNames(), " "));
            processHandler.notifyTextAvailable(greeting, ProcessOutputTypes.SYSTEM);
            task.execute(new ExternalSystemTaskNotificationListenerAdapter() {
              
              private boolean myResetGreeting = true;
              
              @Override
              public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
                if (myResetGreeting) {
                  processHandler.notifyTextAvailable("\r", ProcessOutputTypes.SYSTEM);
                  myResetGreeting = false;
                }
                processHandler.notifyTextAvailable(text, stdOut ? ProcessOutputTypes.STDOUT : ProcessOutputTypes.STDERR);
              }

              @Override
              public void onEnd(@NotNull ExternalSystemTaskId id) {
                processHandler.notifyProcessTerminated(0);
              }
            });
          }
        });
        return new DefaultExecutionResult(console, processHandler);
      }

      @Override
      public RunnerSettings getRunnerSettings() {
        return null;
      }

      @Override
      public ConfigurationPerRunnerSettings getConfigurationSettings() {
        return null;
      }
    };
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    //To change body of implemented methods use File | Settings | File Templates.
  }
  
  private static class MyProcessHandler extends ProcessHandler {
    @Override
    protected void destroyProcessImpl() {
    }

    @Override
    protected void detachProcessImpl() {
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
  }
}
