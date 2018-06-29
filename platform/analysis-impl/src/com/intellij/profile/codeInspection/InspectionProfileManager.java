// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection;

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
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

  void setRootProfile(@Nullable String name);

  @NotNull
  InspectionProfileImpl getCurrentProfile();

  InspectionProfileImpl getProfile(@NotNull String name, boolean returnRootProfileIfNamedIsAbsent);

  default InspectionProfileImpl getProfile(@NotNull String name) {
    return getProfile(name, true);
  }

  @NotNull
  SeverityRegistrar getSeverityRegistrar();

  @NotNull
  @Deprecated
  default SeverityRegistrar getOwnSeverityRegistrar() {
    return getSeverityRegistrar();
  }
}