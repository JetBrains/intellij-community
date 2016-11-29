/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.util.containers.SortedList;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class ProjectStructureProblemsSettingsBase extends ProjectStructureProblemsSettings implements PersistentStateComponent<ProjectStructureProblemsSettingsBase> {
  @AbstractCollection(surroundWithTag = false, elementTag = "problem", elementValueAttribute = "id")
  @Tag("ignored-problems")
  public List<String> myIgnoredProblems = new SortedList<>(String.CASE_INSENSITIVE_ORDER);

  @Override
  public ProjectStructureProblemsSettingsBase getState() {
    return this;
  }

  @Override
  public void loadState(ProjectStructureProblemsSettingsBase state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public boolean isIgnored(@NotNull ProjectStructureProblemDescription description) {
    return myIgnoredProblems.contains(description.getId());
  }

  @Override
  public void setIgnored(@NotNull ProjectStructureProblemDescription description, boolean ignored) {
    final String id = description.getId();
    if (ignored) {
      myIgnoredProblems.add(id);
    }
    else {
      myIgnoredProblems.remove(id);
    }
  }
}
