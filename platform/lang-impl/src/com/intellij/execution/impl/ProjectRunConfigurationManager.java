/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.impl;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.UniqueNameGenerator;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 28-Mar-2006
 */
@State(
  name = "ProjectRunConfigurationManager",
  storages = {
    @Storage(id = "default", file = "$PROJECT_FILE$")
   ,@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/runConfigurations/", scheme = StorageScheme.DIRECTORY_BASED, stateSplitter = ProjectRunConfigurationManager.RunConfigurationStateSplitter.class)
    }
)
public class ProjectRunConfigurationManager implements ProjectComponent, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.ProjectRunConfigurationManager");

  private final RunManagerImpl myManager;
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

  public Element getState() {
     try {
       final Element e = new Element("state");
       writeExternal(e);
       return e;
     }
     catch (WriteExternalException e1) {
       LOG.error(e1);
       return null;
     }
   }

   public void loadState(Element state) {
     try {
       readExternal(state);
     }
     catch (InvalidDataException e) {
       LOG.error(e);
     }
   }

  public void readExternal(Element element) throws InvalidDataException {
    myUnloadedElements = null;
    final Set<String> existing = new HashSet<String>();

    final List children = element.getChildren();
    for (final Object child : children) {
      final RunnerAndConfigurationSettings configuration = myManager.loadConfiguration((Element)child, true);
      if (configuration == null && Comparing.strEqual(element.getName(), RunManagerImpl.CONFIGURATION)) {
        if (myUnloadedElements == null) myUnloadedElements = new ArrayList<Element>(2);
        myUnloadedElements.add(element);
      }

      if (configuration != null) {
        existing.add(RunManagerImpl.getUniqueName(configuration.getConfiguration()));
      }
    }

    myManager.removeNotExistingSharedConfigurations(existing);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final Collection<RunnerAndConfigurationSettings> configurations = myManager.getStableConfigurations().values();
    for (RunnerAndConfigurationSettings configuration : configurations) {
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

  public static class RunConfigurationStateSplitter implements StateSplitter {
    public List<Pair<Element, String>> splitState(Element e) {
      final UniqueNameGenerator generator = new UniqueNameGenerator();

      List<Pair<Element, String>> result = new ArrayList<Pair<Element, String>>();

      final List list = e.getChildren();
      for (final Object o : list) {
        Element library = (Element)o;
        final String name = generator.generateUniqueName(FileUtil.sanitizeFileName(library.getAttributeValue(RunManagerImpl.NAME_ATTR))) + ".xml";
        result.add(new Pair<Element, String>(library, name));
      }

      return result;
    }

    public void mergeStatesInto(Element target, Element[] elements) {
      for (Element e : elements) {
        target.addContent(e);
      }
    }
  }
}
