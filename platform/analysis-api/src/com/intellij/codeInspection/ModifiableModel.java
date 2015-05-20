/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.profile.Profile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * User: anna
 * Date: 15-Feb-2006
 */
public interface ModifiableModel extends Profile {

  InspectionProfile getParentProfile();

  @Nullable
  String getBaseProfileName();

  @Deprecated // use corresponding constructor instead
  void setBaseProfile(InspectionProfile profile);

  void enableTool(@NotNull String inspectionTool, NamedScope namedScope, Project project);

  void disableTool(@NotNull String inspectionTool, NamedScope namedScope, @NotNull Project project);

  void setErrorLevel(HighlightDisplayKey key, @NotNull HighlightDisplayLevel level, Project project);

  HighlightDisplayLevel getErrorLevel(HighlightDisplayKey inspectionToolKey, PsiElement element);

  boolean isToolEnabled(HighlightDisplayKey key);

  boolean isToolEnabled(HighlightDisplayKey key, PsiElement element);

  void commit() throws IOException;

  boolean isChanged();

  void setModified(final boolean toolsSettingsChanged);

  boolean isProperSetting(@NotNull String toolId);

  void resetToBase(Project project);

  void resetToEmpty(Project project);

  /**
   * @return {@link com.intellij.codeInspection.ex.InspectionToolWrapper}
   * @see #getUnwrappedTool(String, com.intellij.psi.PsiElement)
   */
  InspectionToolWrapper getInspectionTool(String shortName, PsiElement element);

  InspectionProfileEntry getUnwrappedTool(@NotNull String shortName, @NotNull PsiElement element);

  InspectionToolWrapper[] getInspectionTools(PsiElement element);

  void copyFrom(@NotNull InspectionProfile profile);

  void setEditable(String toolDisplayName);

  void save() throws IOException;

  boolean isProfileLocked();

  void lockProfile(boolean isLocked);

  void disableTool(@NotNull String toolId, @NotNull PsiElement element);

  void disableTool(@NotNull String inspectionTool, Project project);
}
