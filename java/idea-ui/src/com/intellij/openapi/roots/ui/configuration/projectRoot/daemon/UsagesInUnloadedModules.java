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
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.roots.ui.configuration.GeneralProjectSettingsElement;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.ui.navigation.Place;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

/**
 * @author nik
 */
public class UsagesInUnloadedModules extends ProjectStructureElementUsage {
  private final StructureConfigurableContext myContext;
  private final GeneralProjectSettingsElement myContainingElement;
  private final ProjectStructureElement mySourceElement;
  private final String myPresentableName;

  public UsagesInUnloadedModules(@NotNull StructureConfigurableContext context, @NotNull GeneralProjectSettingsElement element,
                                 @NotNull ProjectStructureElement sourceElement, @NotNull Collection<UnloadedModuleDescription> unloadedModules) {
    myContext = context;
    myContainingElement = element;
    mySourceElement = sourceElement;
    myPresentableName =
      unloadedModules.size() > 1 ? unloadedModules.size() + " Unloaded Modules"
                                 : "Unloaded Module '" + ObjectUtils.notNull(ContainerUtil.getFirstItem(unloadedModules)).getName() + "'";
  }

  @Override
  public ProjectStructureElement getSourceElement() {
    return mySourceElement;
  }

  @Override
  public ProjectStructureElement getContainingElement() {
    return myContainingElement;
  }

  @Override
  public String getPresentableName() {
    return myPresentableName;
  }

  @Override
  public PlaceInProjectStructure getPlace() {
    Place configurablePlace = ProjectStructureConfigurable.getInstance(myContext.getProject()).createProjectConfigurablePlace();
    return new PlaceInProjectStructureBase(myContext.getProject(), configurablePlace, myContainingElement, false);
  }

  @Override
  public void removeSourceElement() {
  }

  @Override
  public void replaceElement(ProjectStructureElement newElement) {

  }

  @Override
  public Icon getIcon() {
    return AllIcons.Modules.UnloadedModule;
  }

  @Override
  public int hashCode() {
    return mySourceElement.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof UsagesInUnloadedModules && mySourceElement.equals(((UsagesInUnloadedModules)obj).mySourceElement);
  }
}
