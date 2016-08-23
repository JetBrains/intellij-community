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
package com.intellij.execution.configurations;

import com.intellij.diagnostic.logging.LogConsole;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Standard base class for run configuration implementations.
 *
 * @author dyoma
 */
public abstract class RunConfigurationBase extends UserDataHolderBase implements RunConfiguration, TargetAwareRunProfile {
  private static final String LOG_FILE = "log_file";
  private static final String PREDEFINED_LOG_FILE_ELEMENT = "predefined_log_file";
  private static final String FILE_OUTPUT = "output_file";
  private static final String SAVE = "is_save";
  private static final String OUTPUT_FILE = "path";
  private static final String SHOW_CONSOLE_ON_STD_OUT = "show_console_on_std_out";
  private static final String SHOW_CONSOLE_ON_STD_ERR = "show_console_on_std_err";

  private final ConfigurationFactory myFactory;
  private final Project myProject;
  private String myName = "";
  private final Icon myIcon;

  private ArrayList<LogFileOptions> myLogFiles = new ArrayList<>();
  private ArrayList<PredefinedLogFile> myPredefinedLogFiles = new ArrayList<>();
  private boolean mySaveOutput = false;
  private boolean myShowConsoleOnStdOut = false;
  private boolean myShowConsoleOnStdErr = false;
  private String myFileOutputPath = null;

  protected RunConfigurationBase(@NotNull Project project, @NotNull ConfigurationFactory factory, final String name) {
    myProject = project;
    myFactory = factory;
    myName = name;
    myIcon = factory.getIcon();
  }

  @Override
  public int getUniqueID() {
    return System.identityHashCode(this);
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
  @NotNull
  public ConfigurationType getType() {
    return myFactory.getType();
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
    runConfiguration.myLogFiles = new ArrayList<>(myLogFiles);
    runConfiguration.myPredefinedLogFiles = new ArrayList<>(myPredefinedLogFiles);
    runConfiguration.myFileOutputPath = myFileOutputPath;
    runConfiguration.mySaveOutput = mySaveOutput;
    runConfiguration.myShowConsoleOnStdOut = myShowConsoleOnStdOut;
    runConfiguration.myShowConsoleOnStdErr = myShowConsoleOnStdErr;
    copyCopyableDataTo(runConfiguration);
    return runConfiguration;
  }

  @Nullable
  public LogFileOptions getOptionsForPredefinedLogFile(PredefinedLogFile predefinedLogFile) {
    return null;
  }

  public void removeAllPredefinedLogFiles() {
    myPredefinedLogFiles.clear();
  }

  public void addPredefinedLogFile(PredefinedLogFile predefinedLogFile) {
    myPredefinedLogFiles.add(predefinedLogFile);
  }

  public ArrayList<PredefinedLogFile> getPredefinedLogFiles() {
    return myPredefinedLogFiles;
  }

  @NotNull
  public ArrayList<LogFileOptions> getAllLogFiles() {
    ArrayList<LogFileOptions> list = new ArrayList<>(myLogFiles);
    for (PredefinedLogFile predefinedLogFile : myPredefinedLogFiles) {
      final LogFileOptions options = getOptionsForPredefinedLogFile(predefinedLogFile);
      if (options != null) {
        list.add(options);
      }
    }
    return list;
  }

  public ArrayList<LogFileOptions> getLogFiles() {
    return myLogFiles;
  }

  public void addLogFile(String file, String alias, boolean checked){
    myLogFiles.add(new LogFileOptions(alias, file, checked, true, false));
  }

  public void addLogFile(String file, String alias, boolean checked, boolean skipContent, final boolean showAll){
    myLogFiles.add(new LogFileOptions(alias, file, checked, skipContent, showAll));
  }

  public void removeAllLogFiles() {
    myLogFiles.clear();
  }

  //invoke before run/debug tabs are shown.
  //Should be overridden to add additional tabs for run/debug toolwindow
  public void createAdditionalTabComponents(AdditionalTabComponentManager manager, ProcessHandler startedProcess) {
  }

  public void customizeLogConsole(LogConsole console) {
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    myLogFiles.clear();
    for (final Object o : element.getChildren(LOG_FILE)) {
      LogFileOptions logFileOptions = new LogFileOptions();
      logFileOptions.readExternal((Element)o);
      myLogFiles.add(logFileOptions);
    }
    myPredefinedLogFiles.clear();
    final List list = element.getChildren(PREDEFINED_LOG_FILE_ELEMENT);
    for (Object fileElement : list) {
      final PredefinedLogFile logFile = new PredefinedLogFile();
      logFile.readExternal((Element)fileElement);
      myPredefinedLogFiles.add(logFile);
    }
    final Element fileOutputElement = element.getChild(FILE_OUTPUT);
    if (fileOutputElement != null) {
      myFileOutputPath = fileOutputElement.getAttributeValue(OUTPUT_FILE);
      String isSave = fileOutputElement.getAttributeValue(SAVE);
      mySaveOutput = isSave != null && Boolean.parseBoolean(isSave);
    }

    if (!isNewSerializationUsed()) {
      myShowConsoleOnStdOut = Boolean.parseBoolean(element.getAttributeValue(SHOW_CONSOLE_ON_STD_OUT));
      myShowConsoleOnStdErr = Boolean.parseBoolean(element.getAttributeValue(SHOW_CONSOLE_ON_STD_ERR));
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    JDOMExternalizerUtil.addChildren(element, LOG_FILE, myLogFiles);
    JDOMExternalizerUtil.addChildren(element, PREDEFINED_LOG_FILE_ELEMENT, myPredefinedLogFiles);

    if (myFileOutputPath != null || mySaveOutput) {
      Element fileOutputPathElement = new Element(FILE_OUTPUT);
      if (myFileOutputPath != null) {
        fileOutputPathElement.setAttribute(OUTPUT_FILE, myFileOutputPath);
      }
      if (mySaveOutput) {
        fileOutputPathElement.setAttribute(SAVE, String.valueOf(mySaveOutput));
      }
      element.addContent(fileOutputPathElement);
    }

    if (!isNewSerializationUsed()) {
      if (myShowConsoleOnStdOut) {//default value shouldn't be written
        element.setAttribute(SHOW_CONSOLE_ON_STD_OUT, String.valueOf(true));
      }
      if (myShowConsoleOnStdErr) {//default value shouldn't be written
        element.setAttribute(SHOW_CONSOLE_ON_STD_ERR, String.valueOf(true));
      }
    }
  }

  protected boolean isNewSerializationUsed() {
    return false;
  }

  @Transient
  public boolean isSaveOutputToFile() {
    return mySaveOutput;
  }

  public void setSaveOutputToFile(boolean redirectOutput) {
    mySaveOutput = redirectOutput;
  }

  @Attribute(SHOW_CONSOLE_ON_STD_OUT)
  public boolean isShowConsoleOnStdOut() {
    return myShowConsoleOnStdOut;
  }

  public void setShowConsoleOnStdOut(boolean showConsoleOnStdOut) {
    myShowConsoleOnStdOut = showConsoleOnStdOut;
  }

  @Attribute(SHOW_CONSOLE_ON_STD_ERR)
  public boolean isShowConsoleOnStdErr() {
    return myShowConsoleOnStdErr;
  }

  public void setShowConsoleOnStdErr(boolean showConsoleOnStdErr) {
    myShowConsoleOnStdErr = showConsoleOnStdErr;
  }

  @Transient
  public String getOutputFilePath() {
    return myFileOutputPath;
  }

  public void setFileOutputPath(String fileOutputPath) {
    myFileOutputPath = fileOutputPath;
  }

  public boolean collectOutputFromProcessHandler() {
    return true;
  }

  public boolean excludeCompileBeforeLaunchOption() {
    return false;
  }

  /**
   * @return true if "Make" Before Launch task should be added automatically on run configuration creation
   * @see RunProfileWithCompileBeforeLaunchOption
   */
  public boolean isCompileBeforeLaunchAddedByDefault() {
    return true;
  }

  @Override
  public String toString() {
    return getType().getDisplayName() + ": " + getName();
  }

  @SuppressWarnings("deprecation")
  @Override
  public ConfigurationPerRunnerSettings createRunnerSettings(ConfigurationInfoProvider provider) {
    return null;
  }

  @Override
  public SettingsEditor<ConfigurationPerRunnerSettings> getRunnerSettingsEditor(ProgramRunner runner) {
    return null;
  }
}
