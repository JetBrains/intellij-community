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

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface InspectionProfileManager {
  String INSPECTION_DIR = "inspection";

  @NotNull
  Collection<InspectionProfileImpl> getProfiles();

  default NamedScopesHolder getScopesManager() {
    return null;
  }

  @NotNull
  static InspectionProfileManager getInstance() {
    return ServiceManager.getService(InspectionProfileManager.class);
  }

  @NotNull
  static InspectionProfileManager getInstance(@NotNull Project project) {
    return InspectionProjectProfileManager.getInstance(project);
  }

  void fireProfileChanged(@Nullable InspectionProfileImpl profile);

  void fireProfileChanged(@Nullable InspectionProfile oldProfile, @NotNull InspectionProfile profile);

  void setRootProfile(@Nullable String name);

  @NotNull
  @Deprecated
  default InspectionProfile getRootProfile() {
    return getCurrentProfile();
  }

  @NotNull
  InspectionProfileImpl getCurrentProfile();

  InspectionProfileImpl getProfile(@NotNull String name, boolean returnRootProfileIfNamedIsAbsent);

  default InspectionProfileImpl getProfile(@NotNull String name) {
    return getProfile(name, true);
  }

  void addProfileChangeListener(@NotNull ProfileChangeAdapter listener, @NotNull Disposable parent);

  @NotNull
  SeverityRegistrar getSeverityRegistrar();

  @NotNull
  SeverityRegistrar getOwnSeverityRegistrar();
}