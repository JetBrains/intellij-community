/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.UnknownRunConfiguration;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SmartList;
import com.intellij.util.text.UniqueNameGenerator;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 28-Mar-2006
 */
@State(
  name = "ProjectRunConfigurationManager",
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_FILE),
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/runConfigurations/", scheme = StorageScheme.DIRECTORY_BASED,
             stateSplitter = ProjectRunConfigurationManager.RunConfigurationStateSplitter.class)
  }
)
public class ProjectRunConfigurationManager implements ProjectComponent, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(ProjectRunConfigurationManager.class);

  private final RunManagerImpl myManager;
  private List<Element> myUnloadedElements;

  public ProjectRunConfigurationManager(final RunManagerImpl manager) {
    myManager = manager;
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  @NotNull
  @NonNls
  public String getComponentName() {
    return "ProjectRunConfigurationManager";
  }

  @Override
  public void initComponent() {

  }

  @Override
  public void disposeComponent() {

  }

  @Override
  public Element getState() {
    Element e = new Element("state");
    for (RunnerAndConfigurationSettings configuration : myManager.getStableConfigurations(true)) {
      myManager.addConfigurationElement(e, configuration);
    }
    if (myUnloadedElements != null) {
      for (Element unloadedElement : myUnloadedElements) {
        e.addContent(unloadedElement.clone());
      }
    }
    return e;
  }

  @Override
  public void loadState(Element state) {
    Set<String> existing = new THashSet<String>();
    try {
      myUnloadedElements = null;
      for (Element child : state.getChildren()) {
        RunnerAndConfigurationSettings configuration = myManager.loadConfiguration(child, true);
        if (configuration == null && Comparing.strEqual(state.getName(), RunManagerImpl.CONFIGURATION)) {
          if (myUnloadedElements == null) {
            myUnloadedElements = new ArrayList<Element>(2);
          }
          myUnloadedElements.add(state);
        }

        if (configuration != null) {
          existing.add(configuration.getUniqueID());
        }
      }
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }

    myManager.removeNotExistingSharedConfigurations(existing);
    if (myManager.getSelectedConfiguration() == null) {
      final List<RunConfiguration> allConfigurations = myManager.getAllConfigurationsList();
      for (final RunConfiguration configuration : allConfigurations) {
        final RunnerAndConfigurationSettings settings = myManager.getSettings(allConfigurations.get(0));
        if (!(configuration instanceof UnknownRunConfiguration)) {
          myManager.setSelectedConfiguration(settings);
          break;
        }
      }
    }

    // IDEA-60004: configs may never be sorted before write, so call it manually after shared configs read
    myManager.setOrdered(false);
    myManager.getSortedConfigurations();
  }

  public static class RunConfigurationStateSplitter implements StateSplitter {
    @Override
    public List<Pair<Element, String>> splitState(Element e) {
      UniqueNameGenerator generator = new UniqueNameGenerator();
      List<Pair<Element, String>> result = new SmartList<Pair<Element, String>>();
      for (Element state : e.getChildren()) {
        result.add(Pair.create(state, generator.generateUniqueName(FileUtil.sanitizeFileName(state.getAttributeValue(RunManagerImpl.NAME_ATTR))) + ".xml"));
      }
      return result;
    }

    @Override
    public void mergeStatesInto(Element target, Element[] elements) {
      for (Element e : elements) {
        target.addContent(e);
      }
    }
  }
}
