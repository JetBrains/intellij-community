// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;

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

  @Override
  public @NotNull ProjectStructureElement getContainingElement() {
    return myElement;
  }

  @Override
  public @NotNull ActionCallback navigate() {
    return ProjectStructureConfigurable.getInstance(myProject).navigateTo(myPlace, true);
  }
}
