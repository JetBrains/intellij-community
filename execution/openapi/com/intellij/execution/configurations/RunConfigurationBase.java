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
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author dyoma
 */
public abstract class RunConfigurationBase implements RunConfiguration {
  private final ConfigurationFactory myFactory;
  private final Project myProject;
  private String myName = "";

  private ArrayList<LogFileOptions> myLogFiles = new ArrayList<LogFileOptions>();
  @NonNls private static final String LOG_FILE = "log_file";
  @NonNls private static final String PATH = "path";
  @NonNls private static final String CHECKED = "checked";
  @NonNls private static final String ALIAS = "alias";
  @NonNls private static final String SKIPPED = "skipped";

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
      return runConfiguration;
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public ArrayList<LogFileOptions> getLogFiles() {
    return myLogFiles;
  }

  public void addLogFile(String file, String alias, boolean checked){
    myLogFiles.add(new LogFileOptions(alias, file, checked, true));
  }

  public void addLogFile(String file, String alias, boolean checked, boolean skipContent){
    myLogFiles.add(new LogFileOptions(alias, file, checked, skipContent));
  }

  public void removeAllLogFiles(){
    myLogFiles.clear();
  }

  public boolean noLogFilesExist() {
    return myLogFiles.isEmpty();
  }

  public void readExternal(Element element) throws InvalidDataException {
    myLogFiles.clear();
    for (Iterator<Element> iterator = element.getChildren(LOG_FILE).iterator(); iterator.hasNext();) {
      Element logFile = iterator.next();
      String file = logFile.getAttributeValue(PATH);
      if (file != null){
        file = FileUtil.toSystemDependentName(file);
      }
      Boolean checked = Boolean.valueOf(logFile.getAttributeValue(CHECKED));
      final String skipped = logFile.getAttributeValue(SKIPPED);
      Boolean skip = skipped != null ? Boolean.valueOf(skipped) : Boolean.TRUE;
      String alias = logFile.getAttributeValue(ALIAS);
      addLogFile(file, alias, checked.booleanValue(), skip.booleanValue());
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (final LogFileOptions options : myLogFiles) {
      Element logFile = new Element(LOG_FILE);
      logFile.setAttribute(PATH, FileUtil.toSystemIndependentName(options.getPath()));
      logFile.setAttribute(CHECKED, String.valueOf(options.isEnabled()));
      logFile.setAttribute(SKIPPED, String.valueOf(options.isSkipContent()));
      logFile.setAttribute(ALIAS, options.getName());
      element.addContent(logFile);
    }
  }

  public static class LogFileOptions {
    private String myName;
    private String myPath;
    private boolean myEnabled;
    private boolean mySkipContent;

    public LogFileOptions(String name,
                          String path,
                          boolean enabled,
                          boolean skipContent) {
      myName = name;
      myPath = path;
      myEnabled = enabled;
      mySkipContent = skipContent;
    }

    public String getName() {
      return myName;
    }

    public String getPath() {
      return myPath;
    }

    public boolean isEnabled() {
      return myEnabled;
    }

    public boolean isSkipContent() {
      return mySkipContent;
    }

    public void setEnable(boolean enable) {
      myEnabled = enable;
    }
  }
}
