// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.build.BuildProgressListener;
import com.intellij.build.BuildViewManager;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.console.DuplexConsoleView;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.*;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.configuration.ExternalSystemRunConfigurationExtensionManager;
import com.intellij.openapi.externalSystem.service.execution.configuration.ExternalSystemRunConfigurationFragmentedEditor;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.search.ExecutionSearchScopes;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.function.Consumer;

import static com.intellij.openapi.externalSystem.service.execution.ExternalSystemProcessHandler.SOFT_PROCESS_KILL_ENABLED_KEY;

public class ExternalSystemRunConfiguration extends LocatableConfigurationBase implements SearchScopeProvidingRunProfile {

  private static final Logger LOG = Logger.getInstance(ExternalSystemRunConfiguration.class);

  private ExternalSystemTaskExecutionSettings mySettings = new ExternalSystemTaskExecutionSettings();

  public static final Key<InputStream> RUN_INPUT_KEY = Key.create("RUN_INPUT_KEY");
  public static final Key<Class<? extends BuildProgressListener>> PROGRESS_LISTENER_KEY = Key.create("PROGRESS_LISTENER_KEY");
  public static final Key<Boolean> DEBUG_SERVER_PROCESS_KEY = ExternalSystemExecutionSettings.DEBUG_SERVER_PROCESS_KEY;

  private static final String DEBUG_SERVER_PROCESS_NAME = "ExternalSystemDebugServerProcess";
  private static final String REATTACH_DEBUG_PROCESS_NAME = "ExternalSystemReattachDebugProcess";
  private static final String DEBUG_DISABLED = "ExternalSystemDebugDisabled";

  private boolean isDebugServerProcess = true;
  private boolean isReattachDebugProcess = false;

  /**
   * Determines if debugging should be disabled for this run configuration.
   * @see com.intellij.openapi.externalSystem.service.execution.ExternalSystemTaskDebugRunner
   * When true, this will cause the debug action to be hidden in the IDE unless another debug runner accepts the run configuration.
   */
  private boolean isDebuggingDisabled = false;

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

  public boolean isReattachDebugProcess() {
    return isReattachDebugProcess;
  }

  public void setReattachDebugProcess(boolean reattachDebugProcess) {
    isReattachDebugProcess = reattachDebugProcess;
  }

  public boolean isDebugServerProcess() {
    return isDebugServerProcess;
  }

  public void setDebugServerProcess(boolean debugServerProcess) {
    isDebugServerProcess = debugServerProcess;
    putUserData(DEBUG_SERVER_PROCESS_KEY, debugServerProcess);
  }

  public boolean isDebuggingDisabled() {
    return isDebuggingDisabled;
  }

  public void setDebuggingDisabled(boolean debuggingDisabled) {
    this.isDebuggingDisabled = debuggingDisabled;
  }

  /**
   * This setting will be delivered with user data to {@link ExternalSystemProcessHandler} and used there.
   * @see com.intellij.execution.process.SoftlyKillableProcessHandler#shouldKillProcessSoftly()
   */
  @ApiStatus.Experimental
  protected void setSoftProcessKillEnabled(boolean softProcessKillEnabled) {
    putUserData(SOFT_PROCESS_KILL_ENABLED_KEY, softProcessKillEnabled);
  }

  @Override
  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public ExternalSystemRunConfiguration clone() {
    ConfigurationFactory configurationFactory = getFactory();
    if (configurationFactory == null) {
      return null;
    }

    final Element element = new Element("toClone");
    try {
      writeExternal(element);
      RunConfiguration clone = configurationFactory.createTemplateConfiguration(getProject());
      ExternalSystemRunConfiguration configuration = (ExternalSystemRunConfiguration)clone;
      configuration.setName(getName());
      configuration.readExternal(element);
      configuration.initializeSettings();
      return configuration;
    }
    catch (InvalidDataException | WriteExternalException e) {
      LOG.error(e);
      return null;
    }
  }

  private void initializeSettings() {
    if (Strings.isEmptyOrSpaces(mySettings.getExternalProjectPath())) {
      String path = getRootProjectPath();
      if (path != null) {
        mySettings.setExternalProjectPath(path);
      }
    }
  }

  private @Nullable String getRootProjectPath() {
    ProjectSystemId externalSystemId = mySettings.getExternalSystemId();
    AbstractExternalSystemLocalSettings<?> localSettings = ExternalSystemApiUtil.getLocalSettings(getProject(), externalSystemId);
    ExternalProjectPojo externalProject = ContainerUtil.getFirstItem(localSettings.getAvailableProjects().keySet());
    return ObjectUtils.doIfNotNull(externalProject, it -> FileUtil.toCanonicalPath(it.getPath()));
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    Element e = element.getChild(ExternalSystemTaskExecutionSettings.TAG_NAME);
    if (e != null) {
      mySettings = XmlSerializer.deserialize(e, ExternalSystemTaskExecutionSettings.class);
      readExternalBoolean(element, DEBUG_SERVER_PROCESS_NAME, this::setDebugServerProcess);
      readExternalBoolean(element, REATTACH_DEBUG_PROCESS_NAME, this::setReattachDebugProcess);
      readExternalBoolean(element, DEBUG_DISABLED, this::setDebuggingDisabled);
    }
    ExternalSystemRunConfigurationExtensionManager.getInstance().readExternal(this, element);
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    element.addContent(XmlSerializer.serialize(mySettings, new SerializationFilter() {
      @Override
      public boolean accepts(@NotNull Accessor accessor, @NotNull Object bean) {
        // only these fields due to backward compatibility
        return switch (accessor.getName()) {
          case "passParentEnvs" -> !mySettings.isPassParentEnvs();
          case "env" -> !mySettings.getEnv().isEmpty();
          default -> true;
        };
      }
    }));
    writeExternalBoolean(element, DEBUG_SERVER_PROCESS_NAME, isDebugServerProcess());
    writeExternalBoolean(element, REATTACH_DEBUG_PROCESS_NAME, isReattachDebugProcess());
    writeExternalBoolean(element, DEBUG_DISABLED, isDebuggingDisabled());
    ExternalSystemRunConfigurationExtensionManager.getInstance().writeExternal(this, element);
  }

  protected static boolean readExternalBoolean(@NotNull Element element, @NotNull String name, @NotNull Consumer<Boolean> consumer) {
    var childElement = element.getChild(name);
    if (childElement == null) {
      return false;
    }
    var value = Boolean.parseBoolean(childElement.getText());
    consumer.accept(value);
    return true;
  }

  protected static void writeExternalBoolean(@NotNull Element element, @NotNull String name, boolean value) {
    var childElement = new Element(name);
    childElement.setText(String.valueOf(value));
    element.addContent(childElement);
  }

  public @NotNull ExternalSystemTaskExecutionSettings getSettings() {
    return mySettings;
  }

  @Override
  public @NotNull SettingsEditor<ExternalSystemRunConfiguration> getConfigurationEditor() {
    return new ExternalSystemRunConfigurationFragmentedEditor(this);
  }

  @Override
  public @Nullable RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) {
    // DebugExecutor ID  - com.intellij.execution.executors.DefaultDebugExecutor.EXECUTOR_ID
    var isStateForDebug = ToolWindowId.DEBUG.equals(executor.getId());
    var runnableState = new ExternalSystemRunnableState(mySettings, getProject(), isStateForDebug, this, env);
    copyUserDataTo(runnableState);
    return runnableState;
  }

  @Override
  public @Nullable GlobalSearchScope getSearchScope() {
    GlobalSearchScope scope = null;
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(mySettings.getExternalSystemId());
    if (manager != null) {
      scope = manager.getSearchScope(getProject(), mySettings);
    }
    if (scope == null) {
      VirtualFile file = VfsUtil.findFileByIoFile(new File(mySettings.getExternalProjectPath()), false);
      if (file != null) {
        Module module = ProjectFileIndex.getInstance(getProject()).getModuleForFile(file, false);
        if (module != null) {
          scope = ExecutionSearchScopes.executionScope(Collections.singleton(module));
        }
      }
    }
    return scope;
  }

  static void foldGreetingOrFarewell(@Nullable ExecutionConsole consoleView, String text, boolean isGreeting) {
    int limit = 100;
    if (text.length() < limit) {
      return;
    }
    final ConsoleViewImpl consoleViewImpl;
    if (consoleView instanceof ConsoleViewImpl) {
      consoleViewImpl = (ConsoleViewImpl)consoleView;
    }
    else if (consoleView instanceof DuplexConsoleView duplexConsoleView) {
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
      UIUtil.invokeLaterIfNeeded(() -> {
        consoleViewImpl.performWhenNoDeferredOutput(() -> {
          if (!ApplicationManager.getApplication().isDispatchThread()) return;

          Document document = consoleViewImpl.getEditor().getDocument();
          int line = isGreeting ? 0 : document.getLineCount() - 2;
          if (CharArrayUtil.regionMatches(document.getCharsSequence(), document.getLineStartOffset(line), text)) {
            final FoldingModel foldingModel = consoleViewImpl.getEditor().getFoldingModel();
            foldingModel.runBatchFoldingOperation(() -> {
              FoldRegion region = foldingModel.addFoldRegion(document.getLineStartOffset(line),
                                                             document.getLineEndOffset(line) + 1,
                                                             StringUtil.trimLog(text, limit));
              if (region != null) {
                region.setExpanded(false);
              }
            });
          }
        });
      });
    }
  }

  @Override
  public void createAdditionalTabComponents(AdditionalTabComponentManager manager, ProcessHandler startedProcess) {
    RunProfile runProfile = ExecutionManagerImpl.getDelegatedRunProfile(this);
    if (runProfile instanceof RunConfigurationBase<?>) {
      ((RunConfigurationBase<?>)runProfile).createAdditionalTabComponents(manager, startedProcess);
    }
  }

  static class MyTaskRerunAction extends FakeRerunAction {
    private final BuildProgressListener myProgressListener;
    private final RunContentDescriptor myContentDescriptor;
    private final ExecutionEnvironment myEnvironment;

    MyTaskRerunAction(BuildProgressListener progressListener,
                      ExecutionEnvironment environment,
                      RunContentDescriptor contentDescriptor) {
      myProgressListener = progressListener;
      myContentDescriptor = contentDescriptor;
      myEnvironment = environment;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      ExecutionEnvironmentProxy environment = getEnvironmentProxy(event);
      if (environment != null) {
        presentation.setText(ExecutionBundle.messagePointer("rerun.configuration.action.name",
                                                            StringUtil.escapeMnemonics(environment.getRunProfileName())));
        RunContentDescriptor descriptor = getDescriptor(event);
        Icon icon = (descriptor != null && ExecutionManagerImpl.isProcessRunning(getDescriptor(event)))
                    ? AllIcons.Actions.Restart
                    : myProgressListener instanceof BuildViewManager
                      ? AllIcons.Actions.Compile
                      : environment.getIcon();
        presentation.setIcon(icon);
        presentation.setEnabled(isEnabled(event));
        return;
      }

      presentation.setEnabled(false);
    }

    @Override
    protected @Nullable RunContentDescriptor getDescriptor(AnActionEvent event) {
      return myContentDescriptor != null ? myContentDescriptor : super.getDescriptor(event);
    }

    @Override
    protected ExecutionEnvironmentProxy getEnvironmentProxy(@NotNull AnActionEvent event) {
      return new BackendExecutionEnvironmentProxy(myEnvironment);
    }
  }
}
