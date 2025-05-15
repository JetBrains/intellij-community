// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocalInspectionToolWrapper extends InspectionToolWrapper<LocalInspectionTool, LocalInspectionEP> {
  /** This should be used in tests primarily */
  public LocalInspectionToolWrapper(@NotNull LocalInspectionTool tool) {
    super(tool, InspectionToolRegistrar.getInstance().findInspectionEP(tool));
  }

  public LocalInspectionToolWrapper(@NotNull LocalInspectionEP ep) {
    super(ep);
  }

  private LocalInspectionToolWrapper(@NotNull LocalInspectionToolWrapper other) {
    super(other);
  }

  @Override
  public @NotNull LocalInspectionToolWrapper createCopy() {
    return new LocalInspectionToolWrapper(this);
  }

  @Override
  public JobDescriptor @NotNull [] getJobDescriptors(@NotNull GlobalInspectionContext context) {
    return context.getStdJobDescriptors().LOCAL_ANALYSIS_ARRAY;
  }

  public boolean isUnfair() {
    return myEP == null ? getTool() instanceof UnfairLocalInspectionTool : myEP.unfair;
  }

  boolean isDynamicGroup() {
    return myEP == null ? getTool() instanceof DynamicGroupTool : myEP.dynamicGroup;
  }

  @Override
  public @NotNull String getID() {
    return myEP == null ? getTool().getID() : myEP.id == null ? myEP.getShortName() : myEP.id;
  }

  public @Nullable String getAlternativeID() {
    return myEP == null ? getTool().getAlternativeID() : myEP.alternativeId;
  }

  public boolean runForWholeFile() {
    return myEP == null ? getTool().runForWholeFile() : myEP.runForWholeFile;
  }

  public static @Nullable InspectionToolWrapper<?, ?> findTool2RunInBatch(@NotNull Project project,
                                                                          @Nullable PsiElement element,
                                                                          @NotNull String name) {
    InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    InspectionToolWrapper<?, ?> toolWrapper = element == null
                                              ? inspectionProfile.getInspectionTool(name, project)
                                              : inspectionProfile.getInspectionTool(name, element);
    return findTool2RunInBatch(project, element, inspectionProfile, toolWrapper);
  }

  public static @Nullable InspectionToolWrapper<?, ?> findTool2RunInBatch(@NotNull Project project,
                                                                          @Nullable PsiElement element,
                                                                          @NotNull InspectionProfile inspectionProfile,
                                                                          @Nullable InspectionToolWrapper<?, ?> toolWrapper) {
    if (toolWrapper instanceof LocalInspectionToolWrapper && ((LocalInspectionToolWrapper)toolWrapper).isUnfair()) {
      LocalInspectionTool inspectionTool = ((LocalInspectionToolWrapper)toolWrapper).getTool();
      if (inspectionTool instanceof PairedUnfairLocalInspectionTool) {
        String oppositeShortName = ((PairedUnfairLocalInspectionTool)inspectionTool).getInspectionForBatchShortName();
        return element == null ? inspectionProfile.getInspectionTool(oppositeShortName, project)
                               : inspectionProfile.getInspectionTool(oppositeShortName, element);
      }
      return null;
    }
    return toolWrapper;
  }
}
