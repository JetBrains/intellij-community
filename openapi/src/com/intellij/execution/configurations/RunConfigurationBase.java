/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.configurations;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

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

  private Map<String , Boolean> myLogFiles = new HashMap<String, Boolean>();
  private final String LOG_FILE = "log_file";
  private final String PATH = "path";
  private final String CHECKED = "checked";
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
      return (RunConfiguration)super.clone();
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public Map<String, Boolean> getLogFiles() {
    return myLogFiles;
  }

  public void addLogFile(String file, boolean checked){
    myLogFiles.put(file, new Boolean(checked));
  }

  public void removeAllLogFiles(){
    myLogFiles.clear();
  }

  public void readExternal(Element element) throws InvalidDataException {
    for (Iterator<Element> iterator = element.getChildren(LOG_FILE).iterator(); iterator.hasNext();) {
      Element logFile = iterator.next();
      String file = logFile.getAttributeValue(PATH);
      Boolean checked = Boolean.valueOf(logFile.getAttributeValue(CHECKED));
      myLogFiles.put(file, checked);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (Iterator<String > iterator = myLogFiles.keySet().iterator(); iterator.hasNext();) {
      String file = iterator.next();
      boolean checked = myLogFiles.get(file).booleanValue();
      Element logFile = new Element(LOG_FILE);
      logFile.setAttribute(PATH, file);
      logFile.setAttribute(CHECKED, String.valueOf(checked));
      element.addContent(logFile);
    }
  }
}
