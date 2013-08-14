/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.profile.DefaultProjectProfileManager;
import com.intellij.profile.Profile;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 30-Nov-2005
 */
public abstract class InspectionProjectProfileManager extends DefaultProjectProfileManager implements ProjectComponent, SeverityProvider, PersistentStateComponent<Element> {
  public InspectionProjectProfileManager(@NotNull Project project,
                                         @NotNull InspectionProfileManager inspectionProfileManager,
                                         @NotNull DependencyValidationManager holder) {
    super(project, inspectionProfileManager, holder);
  }

  public static InspectionProjectProfileManager getInstance(Project project){
    return project.getComponent(InspectionProjectProfileManager.class);
  }

  @Override
  public String getProfileName() {
    return getInspectionProfile().getName();
  }

  @Override
  public Element getState() {
    try {
      final Element e = new Element("settings");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  @Override
  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  @NotNull
  public InspectionProfile getInspectionProfile(){
    return (InspectionProfile)getProjectProfileImpl();
  }

  /**
   * @deprecated  use {@link #getInspectionProfile()} instead
   */
  @SuppressWarnings({"UnusedDeclaration"})
  @NotNull
  public InspectionProfile getInspectionProfile(PsiElement element){
    return getInspectionProfile();
  }

  public abstract boolean isProfileLoaded();

  @Override
  @NotNull
  @NonNls
  public String getComponentName() {
    return "InspectionProjectProfileManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  public abstract void initProfileWrapper(@NotNull Profile profile);

  @Override
  public Profile getProfile(@NotNull final String name) {
    return getProfile(name, true);
  }
}
