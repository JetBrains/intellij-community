// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.roots.ui.configuration.GeneralProjectSettingsElement;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.ui.navigation.Place;

import javax.swing.*;

public class UsageInProjectSettings extends ProjectStructureElementUsage {
  private final StructureConfigurableContext myContext;
  private final ProjectStructureElement mySourceElement;
  private final String myPresentableName;

  public UsageInProjectSettings(StructureConfigurableContext context,
                                ProjectStructureElement sourceElement, String presentableName) {
    myContext = context;
    mySourceElement = sourceElement;
    myPresentableName = presentableName;
  }

  @Override
  public ProjectStructureElement getSourceElement() {
    return mySourceElement;
  }

  @Override
  public ProjectStructureElement getContainingElement() {
    return new GeneralProjectSettingsElement(myContext);
  }

  @Override
  public String getPresentableName() {
    return myPresentableName;
  }

  @Override
  public PlaceInProjectStructure getPlace() {
    Place configurablePlace = ProjectStructureConfigurable.getInstance(myContext.getProject()).createProjectConfigurablePlace();
    return new PlaceInProjectStructureBase(myContext.getProject(), configurablePlace, getContainingElement(), false);
  }

  @Override
  public int hashCode() {
    return mySourceElement.hashCode() * 31 + myPresentableName.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof UsageInProjectSettings && mySourceElement.equals(((UsageInProjectSettings)obj).mySourceElement)
           && myPresentableName.equals(((UsageInProjectSettings)obj).myPresentableName);
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public void removeSourceElement() {

  }

  @Override
  public void replaceElement(ProjectStructureElement newElement) {
  }
}
