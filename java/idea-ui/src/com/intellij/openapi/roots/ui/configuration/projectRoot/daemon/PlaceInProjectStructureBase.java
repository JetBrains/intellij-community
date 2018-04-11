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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class PlaceInProjectStructureBase extends PlaceInProjectStructure {
  private final Project myProject;
  private final Place myPlace;
  private final ProjectStructureElement myElement;
  private final boolean myCanNavigate;

  public PlaceInProjectStructureBase(Project project, Place place, ProjectStructureElement element) {
    this(project, place, element, true);
  }

  public PlaceInProjectStructureBase(Project project, Place place, ProjectStructureElement element, boolean navigate) {
    myProject = project;
    myPlace = place;
    myElement = element;
    myCanNavigate = navigate;
  }

  @Override
  public String getPlacePath() {
    return null;
  }

  @Override
  public boolean canNavigate() {
    return myCanNavigate;
  }

  @NotNull
  @Override
  public ProjectStructureElement getContainingElement() {
    return myElement;
  }

  @NotNull
  @Override
  public ActionCallback navigate() {
    return ProjectStructureConfigurable.getInstance(myProject).navigateTo(myPlace, true);
  }
}
