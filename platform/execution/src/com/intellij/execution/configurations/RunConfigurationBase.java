// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.configurationStore.ComponentSerializationUtil;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.diagnostic.logging.LogConsole;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.FragmentedSettings;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Standard base class for run configuration implementations.
 */
public abstract class RunConfigurationBase<T> extends UserDataHolderBase implements RunConfiguration, TargetAwareRunProfile,
                                                                                    ConfigurationCreationListener, FragmentedSettings {
  private static final String SHOW_CONSOLE_ON_STD_OUT = "show_console_on_std_out";
  private static final String SHOW_CONSOLE_ON_STD_ERR = "show_console_on_std_err";

  @Nullable
  private final ConfigurationFactory myFactory;
  private final Project myProject;
  private String myName;

  private RunConfigurationOptions myOptions;

  @NotNull
  private List<BeforeRunTask<?>> myBeforeRunTasks = Collections.emptyList();

  protected RunConfigurationBase(@NotNull Project project, @Nullable ConfigurationFactory factory, @Nullable String name) {
    myProject = project;
    myFactory = factory;
    myName = name;
    // must be after factory because factory is used to get options class
    myOptions = createOptions();
  }

  @NotNull
  private RunConfigurationOptions createOptions() {
    return ReflectionUtil.newInstance(getOptionsClass());
  }

  @NotNull
  protected RunConfigurationOptions getOptions() {
    return myOptions;
  }

  @Override
  @NotNull
  @Transient
  public List<BeforeRunTask<?>> getBeforeRunTasks() {
    return myBeforeRunTasks;
  }

  @Override
  public void setBeforeRunTasks(@NotNull List<BeforeRunTask<?>> value) {
    myBeforeRunTasks = value;
  }

  @Nullable
  @Override
  public final ConfigurationFactory getFactory() {
    return myFactory;
  }

  @Override
  public final void setName(String name) {
    myName = name;
  }

  @NotNull
  @Override
  public final Project getProject() {
    return myProject;
  }

  @Override
  public @Nullable Icon getIcon() {
    try {
      return myFactory == null ? null : myFactory.getIcon();
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e){
      Logger.getInstance(RunConfigurationBase.class).error(e);
      return null;
    }
  }

  @NotNull
  @Override
  @Transient
  public final String getName() {
    // a lot of clients not ready that name can be null and in most cases it is not convenient - just add more work to handle null value
    // in any case for run configuration empty name it is the same as null, we don't need to bother clients and use null
    return StringUtilRt.notNullize(myName);
  }

  @Override
  public final int hashCode() {
    return super.hashCode();
  }

  public void checkRunnerSettings(@NotNull ProgramRunner runner,
                                  @Nullable RunnerSettings runnerSettings,
                                  @Nullable ConfigurationPerRunnerSettings configurationPerRunnerSettings) throws RuntimeConfigurationException {
  }

  public void checkSettingsBeforeRun() throws RuntimeConfigurationException {
  }

  @Override
  public boolean canRunOn(@NotNull ExecutionTarget target) {
    return true;
  }

  public String getProjectPathOnTarget() {
    return getOptions().getProjectPathOnTarget();
  }

  public void setProjectPathOnTarget(String path) {
    getOptions().setProjectPathOnTarget(path);
  }

  @Override
  public final boolean equals(final Object obj) {
    return super.equals(obj);
  }

  @Override
  public RunConfiguration clone() {
    //noinspection unchecked
    RunConfigurationBase<T> result = (RunConfigurationBase<T>)super.clone();
    result.myOptions = createOptions();
    result.doCopyOptionsFrom(this);
    return result;
  }

  protected void doCopyOptionsFrom(@NotNull RunConfigurationBase<T> template) {
    myOptions.copyFrom(template.myOptions);
    myOptions.resetModificationCount();
    myOptions.setAllowRunningInParallel(template.isAllowRunningInParallel());
    myBeforeRunTasks = ContainerUtil.copyList(template.myBeforeRunTasks);
  }

  @Nullable
  public LogFileOptions getOptionsForPredefinedLogFile(PredefinedLogFile predefinedLogFile) {
    return null;
  }

  public void removeAllPredefinedLogFiles() {
    getOptions().getPredefinedLogFiles().clear();
  }

  public void addPredefinedLogFile(@NotNull PredefinedLogFile predefinedLogFile) {
    getOptions().getPredefinedLogFiles().add(predefinedLogFile);
  }

  @NotNull
  public List<PredefinedLogFile> getPredefinedLogFiles() {
    return getOptions().getPredefinedLogFiles();
  }

  @NotNull
  public ArrayList<LogFileOptions> getAllLogFiles() {
    ArrayList<LogFileOptions> list = new ArrayList<>(getLogFiles());
    for (PredefinedLogFile predefinedLogFile : getOptions().getPredefinedLogFiles()) {
      final LogFileOptions options = getOptionsForPredefinedLogFile(predefinedLogFile);
      if (options != null) {
        list.add(options);
      }
    }
    return list;
  }

  @NotNull
  public List<LogFileOptions> getLogFiles() {
    return getOptions().getLogFiles();
  }

  @SuppressWarnings("unused")
  public void addLogFile(String file, String alias, boolean checked) {
    getOptions().getLogFiles().add(new LogFileOptions(alias, file, checked));
  }

  public void addLogFile(String file, String alias, boolean checked, boolean skipContent, final boolean showAll) {
    getOptions().getLogFiles().add(new LogFileOptions(alias, file, checked, skipContent, showAll));
  }

  public void removeAllLogFiles() {
    getOptions().getLogFiles().clear();
  }

  //invoke before run/debug tabs are shown.
  //Should be overridden to add additional tabs for run/debug toolwindow
  public void createAdditionalTabComponents(AdditionalTabComponentManager manager, ProcessHandler startedProcess) {
  }

  public void customizeLogConsole(LogConsole console) {
  }

  @Nullable
  public T getState() {
    //noinspection unchecked
    return (T)getOptions();
  }

  public void loadState(@NotNull T state) {
    if (state instanceof Element) {
      myOptions = XmlSerializer.deserialize((Element)state, getOptionsClass());
    }
    else {
      myOptions = (RunConfigurationOptions)state;
    }
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    boolean isAllowRunningInParallel = myOptions.isAllowRunningInParallel();
    //noinspection unchecked
    loadState((T)element);
    // load state sets myOptions but we need to preserve transient isAllowRunningInParallel
    myOptions.setAllowRunningInParallel(isAllowRunningInParallel);
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    XmlSerializer.serializeObjectInto(myOptions, element);
  }

  @Override
  public @NotNull List<Option> getSelectedOptions() {
    return myOptions.getSelectedOptions();
  }

  @Override
  public void setSelectedOptions(@NotNull List<Option> fragmentIds) {
    myOptions.setSelectedOptions(fragmentIds);
  }

  @ApiStatus.Experimental
  public void setOptionsFromConfigurationFile(@NotNull BaseState state) {
    myOptions.copyFrom(state, /* isMustBeTheSameType= */false);
  }

  // we can break compatibility and make this method final (API is new and used only by our plugins), but let's avoid any inconvenience and mark as "final" after/prior to 2018.3 release.
  /**
   * Do not override this method, use {@link ConfigurationFactory#getOptionsClass()}.
   */
  protected Class<? extends RunConfigurationOptions> getOptionsClass() {
    Class<? extends BaseState> result = myFactory == null ? null : myFactory.getOptionsClass();
    if (result != null) {
      //noinspection unchecked
      return (Class<? extends RunConfigurationOptions>)result;
    }
    else if (this instanceof PersistentStateComponent) {
      PersistentStateComponent instance = (PersistentStateComponent)this;
      return ComponentSerializationUtil.getStateClass(instance.getClass());
    }
    else {
      return getDefaultOptionsClass();
    }
  }

  /**
   * Do not override this method, it is intended to support old (not migrated to options class) run configurations.
   */
  @NotNull
  protected Class<? extends RunConfigurationOptions> getDefaultOptionsClass() {
    return RunConfigurationOptions.class;
  }

  @Transient
  public boolean isSaveOutputToFile() {
    return myOptions.getFileOutput().isSaveOutput();
  }

  public void setSaveOutputToFile(boolean redirectOutput) {
    myOptions.getFileOutput().setSaveOutput(redirectOutput);
  }

  @Attribute(SHOW_CONSOLE_ON_STD_OUT)
  public boolean isShowConsoleOnStdOut() {
    return myOptions.isShowConsoleOnStdOut();
  }

  public void setShowConsoleOnStdOut(boolean showConsoleOnStdOut) {
    myOptions.setShowConsoleOnStdOut(showConsoleOnStdOut);
  }

  @Attribute(SHOW_CONSOLE_ON_STD_ERR)
  public boolean isShowConsoleOnStdErr() {
    return myOptions.isShowConsoleOnStdErr();
  }

  public void setShowConsoleOnStdErr(boolean showConsoleOnStdErr) {
    myOptions.setShowConsoleOnStdErr(showConsoleOnStdErr);
  }

  @Transient
  public @NlsSafe String getOutputFilePath() {
    return myOptions.getFileOutput().getFileOutputPath();
  }

  public void setFileOutputPath(@NlsSafe String fileOutputPath) {
    myOptions.getFileOutput().setFileOutputPath(fileOutputPath);
  }

  public boolean collectOutputFromProcessHandler() {
    return true;
  }

  /**
   * @deprecated Use {@link RunProfileWithCompileBeforeLaunchOption#isExcludeCompileBeforeLaunchOption()}
   */
  @Deprecated
  public boolean excludeCompileBeforeLaunchOption() {
    return false;
  }

  @Override
  public String toString() {
    return getType().getDisplayName() + ": " + getName();
  }

  /**
   * @deprecated Not used anymore.
   */
  @Deprecated(forRemoval = true)
  protected boolean isNewSerializationUsed() {
    return false;
  }

  @Override
  public final boolean isAllowRunningInParallel() {
    return getOptions().isAllowRunningInParallel();
  }

  @Override
  public final void setAllowRunningInParallel(boolean value) {
    getOptions().setAllowRunningInParallel(value);
  }

  /**
   * Called when configuration created via UI (Add Configuration).
   * Suitable to perform some initialization tasks (in most cases it is indicator that you do something wrong, so, please override this method with care and only if really need).
   */
  @Override
  public void onNewConfigurationCreated() {
  }

  @Override
  public void onConfigurationCopied() {
  }
}
