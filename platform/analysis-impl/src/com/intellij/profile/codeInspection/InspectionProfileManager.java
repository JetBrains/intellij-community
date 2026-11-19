// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection;

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jdom.Element;
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

  /**
   * @param context context element
   * @param inspection inspection profile entry instance
   * @return a copy of inspection profile entry initialized with the same options as the specified inspection entry.
   * It could be useful to temporarily modify options in a 'non-physical' copy without breaking an existing physical profile.
   * @param <T> type of the entry
   */
  static @NotNull <T extends InspectionProfileEntry> InspectionProfileEntry getToolCopy(@NotNull PsiElement context, @NotNull T inspection) {
    InspectionToolWrapper<?, ?> tool = getInstance(context.getProject())
      .getCurrentProfile().getInspectionTool(inspection.getShortName(), context);
    if (tool == null) {
      throw new IllegalArgumentException("Tool not found: " + inspection.getShortName());
    }
    InspectionProfileEntry copiedTool = tool.createCopy().getTool();
    if (copiedTool.getClass() != inspection.getClass()) {
      if (copiedTool instanceof GlobalInspectionTool global) {
        LocalInspectionTool local = global.getSharedLocalInspectionTool();
        if (local != null) {
          copiedTool = local;
        }
      }
      if (copiedTool.getClass() != inspection.getClass()) {
        throw new IllegalArgumentException(
          "Invalid class: " + copiedTool.getClass() + "!=" + inspection.getClass() + " (id: " + inspection.getShortName() + ")");
      }
    }
    final Element options = new Element("copy");
    inspection.writeSettings(options);
    copiedTool.readSettings(options);
    return copiedTool;
  }
}