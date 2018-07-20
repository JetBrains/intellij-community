/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInspection.ex.Tools;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface InspectionProfile extends Comparable {
  @NotNull
  String getName();

  HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey inspectionToolKey, PsiElement element);

  /**
   * If you need to modify tool's settings, please use {@link #modifyToolSettings}
   *
   * @return {@link com.intellij.codeInspection.ex.InspectionToolWrapper}
   * @see #getUnwrappedTool(String, com.intellij.psi.PsiElement)
   */
  InspectionToolWrapper getInspectionTool(@NotNull String shortName, @Nullable PsiElement element);

  @Nullable
  InspectionToolWrapper getInspectionTool(@NotNull String shortName, Project project);

  /** Returns (unwrapped) inspection */
  InspectionProfileEntry getUnwrappedTool(@NotNull String shortName, @NotNull PsiElement element);

  /** Returns (unwrapped) inspection */
  <T extends InspectionProfileEntry> T getUnwrappedTool(@NotNull Key<T> shortNameKey, @NotNull PsiElement element);

  /**
   * Allows a plugin to modify the settings of the inspection tool with the specified ID programmatically, without going through
   * the settings dialog.
   *
   * @param shortNameKey the ID of the tool to change.
   * @param psiElement the element for which the settings should be changed.
   * @param toolConsumer the callback that receives the tool.
   * @since 12.1
   */
  <T extends InspectionProfileEntry>
  void modifyToolSettings(@NotNull Key<T> shortNameKey, @NotNull PsiElement psiElement, @NotNull Consumer<T> toolConsumer);

  /**
   * @param element context element
   * @return all (both enabled and disabled) tools
   */
  @NotNull
  InspectionToolWrapper[] getInspectionTools(@Nullable PsiElement element);

  boolean isToolEnabled(@Nullable HighlightDisplayKey key, @Nullable PsiElement element);

  default boolean isToolEnabled(@Nullable HighlightDisplayKey key) {
    return isToolEnabled(key, null);
  }

  boolean isExecutable(@Nullable Project project);

  /**
   * @see com.intellij.codeInspection.ex.InspectionProfileImpl#setSingleTool(String)
   *
   * @return tool short name when inspection profile corresponds to synthetic profile for single inspection run
   */
  @Nullable
  String getSingleTool();

  @NotNull
  String getDisplayName();

  @NotNull
  List<Tools> getAllEnabledInspectionTools(Project project);
}
