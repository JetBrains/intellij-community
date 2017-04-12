/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.execution.configurations.UnknownRunConfiguration;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.StateSplitterEx;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.Pair;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

@State(name = "ProjectRunConfigurationManager", storages = @Storage(value = "runConfigurations", stateSplitter = ProjectRunConfigurationManager.RunConfigurationStateSplitter.class))
public class ProjectRunConfigurationManager implements PersistentStateComponent<Element> {
  private final RunManagerImpl myManager;

  public ProjectRunConfigurationManager(@NotNull RunManagerImpl manager) {
    myManager = manager;
  }

  @Override
  public Element getState() {
    Element state = new Element("state");
    myManager.writeConfigurations(state, myManager.getSharedConfigurations());
    return state;
  }

  @Override
  public void loadState(Element state) {
    Set<String> existing = new THashSet<>();
    for (Element child : state.getChildren(RunManagerImpl.CONFIGURATION)) {
      existing.add(myManager.loadConfiguration(child, true).getUniqueID());
    }

    myManager.removeNotExistingSharedConfigurations(existing);
    myManager.requestSort();

    if (myManager.getSelectedConfiguration() == null) {
      for (RunnerAndConfigurationSettings settings : myManager.getAllSettings()) {
        if (!(settings.getType() instanceof UnknownRunConfiguration)) {
          myManager.setSelectedConfiguration(settings);
          break;
        }
      }
    }
  }

  static class RunConfigurationStateSplitter extends StateSplitterEx {
    @Override
    public List<Pair<Element, String>> splitState(@NotNull Element state) {
      return splitState(state, RunManagerImpl.NAME_ATTR);
    }
  }
}
