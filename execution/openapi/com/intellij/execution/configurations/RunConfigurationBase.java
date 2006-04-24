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

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

/**
 * @author dyoma
 */
public abstract class RunConfigurationBase implements RunConfiguration {
  private final ConfigurationFactory myFactory;
  private final Project myProject;
  private String myName = "";

  private ArrayList<LogFileOptions> myLogFiles = new ArrayList<LogFileOptions>();
  @NonNls private static final String LOG_FILE = "log_file";
  private THashMap<Object, AdditionalTabComponent> myAdditionalTabs = null;

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
    myLogFiles.add(new LogFileOptions(alias, file, checked, true, false));
  }

  public void addLogFile(String file, String alias, boolean checked, boolean skipContent, final boolean showAll){
    myLogFiles.add(new LogFileOptions(alias, file, checked, skipContent, showAll));
  }

  public void removeAllLogFiles(){
    myLogFiles.clear();
  }

  public boolean noLogFilesExist() {
    return myLogFiles.isEmpty();
  }

  public AdditionalTabComponent getAdditionalTabComponent(Object key){
    return myAdditionalTabs != null ? myAdditionalTabs.get(key) : null;
  }

  //invoke before run/debug tabs are shown.
  //Should be overriden to add additional tabs for run/debug toolwindow
  public void createAdditionalTabComponents() {
  }

  public void addAdditionalTab(Object key, AdditionalTabComponent component){
    synchronized(this){
      if (myAdditionalTabs == null){
        if (component == null) return;
        myAdditionalTabs = new THashMap<Object, AdditionalTabComponent>();
      }
      if (component != null){
        //noinspection unchecked
        myAdditionalTabs.put(key, component);
      }
      else{
        myAdditionalTabs.remove(key);
        if (myAdditionalTabs.size() == 0){
          myAdditionalTabs = null;
        }
      }
    }
  }

  public void clearAdditionalTabs() {
    myAdditionalTabs = null;
  }

  @NotNull public Set getAdditionalTabKeys(){
    return myAdditionalTabs != null ? myAdditionalTabs.keySet() : Collections.EMPTY_SET;
  }

  public void readExternal(Element element) throws InvalidDataException {
    myLogFiles.clear();
    for (Iterator<Element> iterator = element.getChildren(LOG_FILE).iterator(); iterator.hasNext();) {
      LogFileOptions logFileOptions = new LogFileOptions();
      logFileOptions.readExternal(iterator.next());
      myLogFiles.add(logFileOptions);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (final LogFileOptions options : myLogFiles) {
      Element logFile = new Element(LOG_FILE);
      options.writeExternal(logFile);
      element.addContent(logFile);
    }
  }

}
