// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.build.BuildProgressListener;
import com.intellij.build.BuildViewManager;
import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.console.DuplexConsoleView;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.FakeRerunAction;
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
import com.intellij.openapi.externalSystem.service.execution.configuration.ExternalSystemRunConfigurationExtensionManager;
import com.intellij.openapi.externalSystem.service.execution.configuration.ExternalSystemRunConfigurationFragmentedEditor;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.InputStream;
import java.util.Collections;

public class ExternalSystemRunConfiguration extends LocatableConfigurationBase implements SearchScopeProvidingRunProfile {
  public static final Key<InputStream> RUN_INPUT_KEY = Key.create("RUN_INPUT_KEY");
  public static final Key<Class<? extends BuildProgressListener>> PROGRESS_LISTENER_KEY = Key.create("PROGRESS_LISTENER_KEY");

  static final Logger LOG = Logger.getInstance(ExternalSystemRunConfiguration.class);
  private ExternalSystemTaskExecutionSettings mySettings = new ExternalSystemTaskExecutionSettings();
  static final boolean DISABLE_FORK_DEBUGGER = Boolean.getBoolean("external.system.disable.fork.debugger");

  public static final String DEBUG_SERVER_PROCESS_NAME = "ExternalSystemDebugServerProcess";
  private static final String REATTACH_DEBUG_PROCESS_NAME = "ExternalSystemReattachDebugProcess";
  private boolean isDebugServerProcess = true;
  private boolean isReattachDebugProcess = false;

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

      final Element debugServerProcess = element.getChild(DEBUG_SERVER_PROCESS_NAME);
      if (debugServerProcess != null) {
        isDebugServerProcess = Boolean.parseBoolean(debugServerProcess.getText());
      }
      final Element reattachProcess = element.getChild(REATTACH_DEBUG_PROCESS_NAME);
      if (reattachProcess != null) {
        isReattachDebugProcess = Boolean.parseBoolean(reattachProcess.getText());
      }
    }
    ExternalSystemRunConfigurationExtensionManager.readExternal(this, element);
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

    final Element debugServerProcess = new Element(DEBUG_SERVER_PROCESS_NAME);
    debugServerProcess.setText(String.valueOf(isDebugServerProcess));
    element.addContent(debugServerProcess);
    final Element reattachProcess = new Element(REATTACH_DEBUG_PROCESS_NAME);
    reattachProcess.setText(String.valueOf(isReattachDebugProcess));
    element.addContent(reattachProcess);

    ExternalSystemRunConfigurationExtensionManager.writeExternal(this, element);
  }

  @NotNull
  public ExternalSystemTaskExecutionSettings getSettings() {
    return mySettings;
  }

  @NotNull
  @Override
  public SettingsEditor<ExternalSystemRunConfiguration> getConfigurationEditor() {
    if (Registry.is("ide.new.run.config", true)) {
      return new ExternalSystemRunConfigurationFragmentedEditor(this);
    }

    SettingsEditorGroup<ExternalSystemRunConfiguration> group = new SettingsEditorGroup<>();
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"),
                    new ExternalSystemRunConfigurationEditor(getProject(), mySettings.getExternalSystemId()));
    ExternalSystemRunConfigurationExtensionManager.appendEditors(this, group);
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<>());
    return group;
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) {
    // DebugExecutor ID  - com.intellij.execution.executors.DefaultDebugExecutor.EXECUTOR_ID
    String debugExecutorId = ToolWindowId.DEBUG;
    ExternalSystemRunnableState
      runnableState = new ExternalSystemRunnableState(mySettings, getProject(), debugExecutorId.equals(executor.getId()), this, env);
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
      ExecutionEnvironment environment = getEnvironment(event);
      if (environment != null) {
        presentation.setText(ExecutionBundle.messagePointer("rerun.configuration.action.name",
                                                     StringUtil.escapeMnemonics(environment.getRunProfile().getName())));
        Icon icon = ExecutionManagerImpl.isProcessRunning(getDescriptor(event))
                    ? AllIcons.Actions.Restart
                    : myProgressListener instanceof BuildViewManager
                      ? AllIcons.Actions.Compile
                      : environment.getExecutor().getIcon();
        presentation.setIcon(icon);
        presentation.setEnabled(isEnabled(event));
        return;
      }

      presentation.setEnabled(false);
    }

    @Nullable
    @Override
    protected RunContentDescriptor getDescriptor(AnActionEvent event) {
      return myContentDescriptor != null ? myContentDescriptor : super.getDescriptor(event);
    }

    @Override
    protected ExecutionEnvironment getEnvironment(@NotNull AnActionEvent event) {
      return myEnvironment;
    }
  }
}
