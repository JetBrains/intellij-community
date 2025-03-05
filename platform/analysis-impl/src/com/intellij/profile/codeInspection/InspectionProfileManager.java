// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection;

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface InspectionProfileManager {
  String INSPECTION_DIR = "inspection";

  @NotNull
  Collection<InspectionProfileImpl> getProfiles();

  default @Nullable NamedScopesHolder getScopesManager() {
    return null;
  }

  static @NotNull InspectionProfileManager getInstance() {
    return ApplicationManager.getApplication().getService(InspectionProfileManager.class);
  }

  static @NotNull InspectionProfileManager getInstance(@NotNull Project project) {
    return InspectionProjectProfileManager.getInstance(project);
  }

  void setRootProfile(@Nullable String name);

  @NotNull
  InspectionProfileImpl getCurrentProfile();

  @Contract("_,true -> !null")
  InspectionProfileImpl getProfile(@NotNull String name, boolean returnRootProfileIfNamedIsAbsent);

  default @NotNull InspectionProfileImpl getProfile(@NotNull String name) {
    return getProfile(name, true);
  }

  @NotNull
  SeverityRegistrar getSeverityRegistrar();

  /**
   * @deprecated use {@link #getSeverityRegistrar()}
   */
  @Deprecated(forRemoval = true)
  default @NotNull SeverityRegistrar getOwnSeverityRegistrar() {
    return getSeverityRegistrar();
  }

  /**
   * Check whether the given inspection's severity in the current profile is lower than {@link LocalInspectionToolSession#getMinimumSeverity()}
   * and thus running the inspection makes no sense as its results will be ignored.
   */
  static boolean hasTooLowSeverity(LocalInspectionToolSession session, LocalInspectionTool inspection) {
    HighlightSeverity minSeverity = session.getMinimumSeverity();
    if (minSeverity == null) return false;

    Project project = session.getFile().getProject();
    ToolsImpl tools = getInstance(project).getCurrentProfile().getToolsOrNull(inspection.getShortName(), project);
    HighlightSeverity profileSeverity = tools == null ? null : tools.getLevel().getSeverity();
    return profileSeverity != null && minSeverity.compareTo(profileSeverity) > 0;
  }
}