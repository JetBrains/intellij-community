package com.intellij.execution.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: anna
 * Date: 28-Mar-2006
 */
public class ProjectRunConfigurationManager implements ProjectComponent, JDOMExternalizable {
  private RunManagerImpl myManager;
  private List<Element> myUnloadedElements = null;

  public ProjectRunConfigurationManager(final RunManagerImpl manager) {
    myManager = manager;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "ProjectRunConfigurationManager";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public void readExternal(Element element) throws InvalidDataException {
    myUnloadedElements = null;

    final List children = element.getChildren();
    for (final Object child : children) {
      if (!myManager.loadConfiguration((Element)child, true) && Comparing.strEqual(element.getName(), RunManagerImpl.CONFIGURATION)) {
        if (myUnloadedElements == null) myUnloadedElements = new ArrayList<Element>(2);
        myUnloadedElements.add(element);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final Collection<RunnerAndConfigurationSettingsImpl> configurations = myManager.getStableConfigurations().values();
    for (RunnerAndConfigurationSettingsImpl configuration : configurations) {
      if (myManager.isConfigurationShared(configuration)){
        myManager.addConfigurationElement(element, configuration);
      }
    }
    if (myUnloadedElements != null) {
      for (Element unloadedElement : myUnloadedElements) {
        element.addContent((Element)unloadedElement.clone());
      }
    }
  }
}
