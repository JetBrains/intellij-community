package com.intellij.execution.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.List;

/**
 * User: anna
 * Date: 28-Mar-2006
 */
public class ProjectRunConfigurationManager implements ProjectComponent, JDOMExternalizable {
  private RunManagerImpl myManager;

  public ProjectRunConfigurationManager(final RunManagerImpl manager) {
    myManager = manager;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  public String getComponentName() {
    return "ProjectRunConfigurationManager";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public void readExternal(Element element) throws InvalidDataException {
    final List children = element.getChildren();
    for (final Object child : children) {
      myManager.loadConfiguration((Element)child, true);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final Collection<RunnerAndConfigurationSettingsImpl> configurations = myManager.getStableConfigurations().values();
    for (RunnerAndConfigurationSettingsImpl configuration : configurations) {
      if (myManager.isConfigurationShared(configuration)){
        myManager.addConfigurationElement(element, configuration);
      }
    }
  }
}
