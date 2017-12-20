/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.configurations;

import com.intellij.configurationStore.ComponentSerializationUtil;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.diagnostic.logging.LogConsole;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Standard base class for run configuration implementations.
 */
public abstract class RunConfigurationBase extends UserDataHolderBase implements RunConfiguration, TargetAwareRunProfile {
  private static final String PREDEFINED_LOG_FILE_ELEMENT = "predefined_log_file";
  private static final String SHOW_CONSOLE_ON_STD_OUT = "show_console_on_std_out";
  private static final String SHOW_CONSOLE_ON_STD_ERR = "show_console_on_std_err";

  private final ConfigurationFactory myFactory;
  private final Project myProject;
  private String myName;
  private final Icon myIcon;

  private RunConfigurationOptions myOptions = createOptions();

  private List<PredefinedLogFile> myPredefinedLogFiles = new SmartList<>();

  private List<BeforeRunTask> myBeforeRunTasks = Collections.emptyList();

  protected RunConfigurationBase(@NotNull Project project, @NotNull ConfigurationFactory factory, String name) {
    myProject = project;
    myFactory = factory;
    myName = name;
    myIcon = factory.getIcon();
  }

  @NotNull
  private RunConfigurationOptions createOptions() {
    return ReflectionUtil.newInstance(getOptionsClass());
  }

  protected RunConfigurationOptions getOptions() {
    return myOptions;
  }

  @Override
  @NotNull
  @Transient
  public List<BeforeRunTask> getBeforeRunTasks() {
    return myBeforeRunTasks;
  }

  @Override
  public void setBeforeRunTasks(@NotNull List<BeforeRunTask> value) {
    myBeforeRunTasks = value;
  }

  @Override
  public final ConfigurationFactory getFactory() {
    return myFactory;
  }

  @Override
  public final void setName(final String name) {
    myName = name;
  }

  @NotNull
  @Override
  public final Project getProject() {
    return myProject;
  }

  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  @Transient
  public final String getName() {
    return myName;
  }

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

  public final boolean equals(final Object obj) {
    return super.equals(obj);
  }

  @Override
  public RunConfiguration clone() {
    final RunConfigurationBase runConfiguration = (RunConfigurationBase)super.clone();
    runConfiguration.myPredefinedLogFiles = new ArrayList<>(myPredefinedLogFiles);

    runConfiguration.myOptions = createOptions();
    runConfiguration.myOptions.copyFrom(myOptions);
    copyCopyableDataTo(runConfiguration);

    myBeforeRunTasks = myBeforeRunTasks.isEmpty() ? Collections.emptyList() : new SmartList<>(myBeforeRunTasks);
    return runConfiguration;
  }

  @Nullable
  public LogFileOptions getOptionsForPredefinedLogFile(PredefinedLogFile predefinedLogFile) {
    return null;
  }

  public void removeAllPredefinedLogFiles() {
    myPredefinedLogFiles.clear();
  }

  public void addPredefinedLogFile(@NotNull PredefinedLogFile predefinedLogFile) {
    myPredefinedLogFiles.add(predefinedLogFile);
  }

  @NotNull
  public List<PredefinedLogFile> getPredefinedLogFiles() {
    return myPredefinedLogFiles;
  }

  @NotNull
  public ArrayList<LogFileOptions> getAllLogFiles() {
    ArrayList<LogFileOptions> list = new ArrayList<>(getLogFiles());
    for (PredefinedLogFile predefinedLogFile : myPredefinedLogFiles) {
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

  public void loadState(@NotNull Element element) {
    readExternal(element);
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    myPredefinedLogFiles.clear();
    for (Element fileElement : element.getChildren(PREDEFINED_LOG_FILE_ELEMENT)) {
      final PredefinedLogFile logFile = new PredefinedLogFile();
      logFile.readExternal(fileElement);
      myPredefinedLogFiles.add(logFile);
    }

    myOptions = XmlSerializer.deserialize(element, getOptionsClass());
  }

  protected Class<? extends RunConfigurationOptions> getOptionsClass() {
    if (this instanceof PersistentStateComponent) {
      PersistentStateComponent instance = (PersistentStateComponent)this;
      return ComponentSerializationUtil.getStateClass(instance.getClass());
    }
    return RunConfigurationOptions.class;
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    JDOMExternalizerUtil.addChildren(element, PREDEFINED_LOG_FILE_ELEMENT, myPredefinedLogFiles);
    XmlSerializer.serializeObjectInto(myOptions, element);
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
  public String getOutputFilePath() {
    return myOptions.getFileOutput().getFileOutputPath();
  }

  public void setFileOutputPath(String fileOutputPath) {
    myOptions.getFileOutput().setFileOutputPath(fileOutputPath);
  }

  public boolean collectOutputFromProcessHandler() {
    return true;
  }

  public boolean excludeCompileBeforeLaunchOption() {
    return false;
  }

  /**
   * @deprecated use {@link RunProfileWithCompileBeforeLaunchOption#isBuildBeforeLaunchAddedByDefault()} instead
   */
  public boolean isCompileBeforeLaunchAddedByDefault() {
    return true;
  }

  @Override
  public String toString() {
    return getType().getDisplayName() + ": " + getName();
  }
}
