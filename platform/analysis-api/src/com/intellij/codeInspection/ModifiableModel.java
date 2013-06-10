/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  void setBaseProfile(InspectionProfile profile);

  void enableTool(String inspectionTool, NamedScope namedScope);

  void disableTool(String inspectionTool, NamedScope namedScope);

  void setErrorLevel(HighlightDisplayKey key, @NotNull HighlightDisplayLevel level);

  HighlightDisplayLevel getErrorLevel(HighlightDisplayKey inspectionToolKey, PsiElement element);

  boolean isToolEnabled(HighlightDisplayKey key);

  boolean isToolEnabled(HighlightDisplayKey key, PsiElement element);

  void commit() throws IOException;

  boolean isChanged();

  void setModified(final boolean toolsSettingsChanged);

  @Deprecated
  boolean isProperSetting(HighlightDisplayKey key);

  boolean isProperSetting(String toolId);

  void resetToBase();

  void resetToEmpty();

  /**
   * @return {@link com.intellij.codeInspection.ex.InspectionToolWrapper}
   * @see #getUnwrappedTool(String, com.intellij.psi.PsiElement)
   */
  InspectionProfileEntry getInspectionTool(String shortName, PsiElement element);

  InspectionProfileEntry getUnwrappedTool(@NotNull String shortName, @NotNull PsiElement element);

  InspectionProfileEntry[] getInspectionTools(PsiElement element);

  void copyFrom(InspectionProfile profile);

  void setEditable(String toolDisplayName);

  void save() throws IOException;

  boolean isProfileLocked();

  void lockProfile(boolean isLocked);

  void disableTool(String toolId, PsiElement element);

  void disableTool(String inspectionTool);
}
