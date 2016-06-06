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
package com.intellij.profile;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.profile.codeInspection.SeverityProvider;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ApplicationProfileManager extends ProfileManager, SeverityProvider {
  String INSPECTION_DIR = "inspection";

  @NotNull
  static ApplicationProfileManager getInstance() {
    return ServiceManager.getService(ApplicationProfileManager.class);
  }

  @Deprecated
  @SuppressWarnings("unused")
  Profile createProfile();

  @SuppressWarnings("unused")
  void addProfileChangeListener(@NotNull ProfileChangeAdapter listener);

  @Deprecated
  @SuppressWarnings("unused")
  void removeProfileChangeListener(@NotNull ProfileChangeAdapter listener);

  void fireProfileChanged(Profile profile);

  void fireProfileChanged(Profile oldProfile, Profile profile, @Nullable NamedScope scope);

  void setRootProfile(@Nullable String profileName);

  @NotNull
  Profile getRootProfile();

  void addProfile(@NotNull Profile profile);

  @Override
  default NamedScopesHolder getScopesManager() {
    return null;
  }
}
