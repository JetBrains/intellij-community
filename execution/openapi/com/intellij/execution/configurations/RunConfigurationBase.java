/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dyoma
 */
public abstract class RunConfigurationBase implements RunConfiguration {
  private final ConfigurationFactory myFactory;
  private final Project myProject;
  private String myName = "";

  private ArrayList<LogFileOptions> myLogFiles = new ArrayList<LogFileOptions>();
  private ArrayList<PredefinedLogFile> myPredefinedLogFiles = new ArrayList<PredefinedLogFile>();
  @NonNls private static final String LOG_FILE = "log_file";
  @NonNls private static final String PREDEFINED_LOG_FILE_ELEMENT = "predefined_log_file";

  protected RunConfigurationBase(final Project project, final ConfigurationFactory factory, final String name) {
    myProject = project;
    myFactory = factory;
    myName = name;
  }

  public final ConfigurationFactory getFactory() {
    return myFactory;
  }

  public final void setName(final String name) {
    myName = name;
  }

  public final Project getProject() {
    return myProject;
  }

  public ConfigurationType getType() {
    return myFactory.getType();
  }

  public final String getName() {
    return myName;
  }

  public final int hashCode() {
    return super.hashCode();
  }

  public final boolean equals(final Object obj) {
    return super.equals(obj);
  }

  public RunConfiguration clone() {
    try {
      final RunConfigurationBase runConfiguration = (RunConfigurationBase)super.clone();
      runConfiguration.myLogFiles = new ArrayList<LogFileOptions>(myLogFiles);
      runConfiguration.myPredefinedLogFiles = new ArrayList<PredefinedLogFile>(myPredefinedLogFiles);
      return runConfiguration;
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public @Nullable LogFileOptions getOptionsForPredefinedLogFile(PredefinedLogFile predefinedLogFile) {
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

  public ArrayList<LogFileOptions> getAllLogFiles() {
    final ArrayList<LogFileOptions> list = new ArrayList<LogFileOptions>(myLogFiles);
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
  //Should be overriden to add additional tabs for run/debug toolwindow
  public void createAdditionalTabComponents(AdditionalTabComponentManager manager) {
  }

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
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (final LogFileOptions options : myLogFiles) {
      Element logFile = new Element(LOG_FILE);
      options.writeExternal(logFile);
      element.addContent(logFile);
    }
    for (PredefinedLogFile predefinedLogFile : myPredefinedLogFiles) {
      Element fileElement = new Element(PREDEFINED_LOG_FILE_ELEMENT);
      predefinedLogFile.writeExternal(fileElement);
      element.addContent(fileElement);
    }
  }

}
