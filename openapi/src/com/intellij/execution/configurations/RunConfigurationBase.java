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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author dyoma
 */
public abstract class RunConfigurationBase implements RunConfiguration {
  private final ConfigurationFactory myFactory;
  private final Project myProject;
  private String myName = "";

  private Map<Pair<String, String >, Boolean> myLogFiles = new HashMap<Pair<String, String>, Boolean>();
  @SuppressWarnings({"HardCodedStringLiteral"})
  private final String LOG_FILE = "log_file";
  @SuppressWarnings({"HardCodedStringLiteral"})
  private final String PATH = "path";
  @SuppressWarnings({"HardCodedStringLiteral"})
  private final String CHECKED = "checked";
  @SuppressWarnings({"HardCodedStringLiteral"})
  private final String ALIAS = "alias";
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
      runConfiguration.myLogFiles = new HashMap<Pair<String, String>, Boolean>(myLogFiles);
      return runConfiguration;
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public Map<Pair<String, String >, Boolean> getLogFiles() {
    return myLogFiles;
  }

  public void addLogFile(String file, String alias, boolean checked){
    myLogFiles.put(Pair.create(file, alias), new Boolean(checked));
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
        file.replace('/', File.separatorChar);
      }
      Boolean checked = Boolean.valueOf(logFile.getAttributeValue(CHECKED));
      String alias = logFile.getAttributeValue(ALIAS);
      addLogFile(file, alias, checked);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (Iterator<Pair<String, String>> iterator = myLogFiles.keySet().iterator(); iterator.hasNext();) {
      final Pair<String, String> pair = iterator.next();
      String file = pair.first;
      String alias = pair.second;
      boolean checked = myLogFiles.get(pair).booleanValue();
      Element logFile = new Element(LOG_FILE);
      logFile.setAttribute(PATH, file.replace(File.separatorChar, '/'));
      logFile.setAttribute(CHECKED, String.valueOf(checked));
      logFile.setAttribute(ALIAS, alias != null ? alias : file);
      element.addContent(logFile);
    }
  }
}
