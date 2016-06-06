/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileManager;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: 30-Nov-2005
 */
public interface InspectionProjectProfileManager extends ProfileManager, SeverityProvider, PersistentStateComponent<Element> {
  static InspectionProjectProfileManager getInstance(Project project){
    return project.getComponent(InspectionProjectProfileManager.class);
  }

  default String getProfileName() {
    return getInspectionProfile().getName();
  }

  @NotNull
  Project getProject();

  @NotNull
  InspectionProfile getInspectionProfile();

  /**
   * @deprecated  use {@link #getInspectionProfile()} instead
   */
  @SuppressWarnings({"UnusedDeclaration"})
  @NotNull
  default InspectionProfile getInspectionProfile(PsiElement element){
    return getInspectionProfile();
  }

  boolean isProfileLoaded();

  void initProfileWrapper(@NotNull Profile profile);

  @Override
  default Profile getProfile(@NotNull final String name) {
    return getProfile(name, true);
  }

  void setProjectProfile(@Nullable String projectProfile);
}
