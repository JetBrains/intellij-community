/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.util.containers.SortedList;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class ProjectStructureProblemsSettingsBase extends ProjectStructureProblemsSettings implements PersistentStateComponent<ProjectStructureProblemsSettingsBase> {
  @XCollection(propertyElementName = "ignored-problems", elementName = "problem", valueAttributeName = "id")
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
