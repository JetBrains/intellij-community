// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public interface InspectionProfile extends Comparable<Object> {
  String DEFAULT_PROFILE_NAME = "Default";

  @NotNull @NlsSafe String getName();

  @NotNull HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey inspectionToolKey, PsiElement element);

  /**
   * If you need to modify tool's settings, please use {@link #modifyToolSettings}
   *
   * @return {@link InspectionToolWrapper}
   * @see #getUnwrappedTool(String, PsiElement)
   */
  @RequiresReadLock
  @Nullable InspectionToolWrapper<?, ?> getInspectionTool(@NotNull String shortName, @Nullable PsiElement element);

  @Nullable InspectionToolWrapper<?, ?> getInspectionTool(@NotNull String shortName, Project project);

  /** Returns (unwrapped) inspection */
  @Nullable InspectionProfileEntry getUnwrappedTool(@NotNull String shortName, @NotNull PsiElement element);

  /** Returns (unwrapped) inspection */
  <T extends InspectionProfileEntry> @Nullable T getUnwrappedTool(@NotNull Key<T> shortNameKey, @NotNull PsiElement element);

  /**
   * Allows a plugin to modify the settings of the inspection tool with the specified ID programmatically, without going through
   * the settings dialog.
   *
   * @param shortNameKey the ID of the tool to change.
   * @param psiElement the element for which the settings should be changed.
   * @param toolConsumer the callback that receives the tool.
   */
  <T extends InspectionProfileEntry> void modifyToolSettings(@NotNull Key<T> shortNameKey, @NotNull PsiElement psiElement, @NotNull Consumer<? super T> toolConsumer);

  /**
   * @param element context element
   * @return all (both enabled and disabled) tools
   */
  @NotNull @Unmodifiable
  List<InspectionToolWrapper<?, ?>> getInspectionTools(@Nullable PsiElement element);

  boolean isToolEnabled(@Nullable HighlightDisplayKey key, @Nullable PsiElement element);

  default boolean isToolEnabled(@Nullable HighlightDisplayKey key) {
    return isToolEnabled(key, null);
  }

  /**
   * @return editor attributes if they are different from attributes of chosen severity, {@code null} otherwise
   */
  default @Nullable TextAttributesKey getEditorAttributes(@NotNull String shortName, @Nullable PsiElement element) {
    var tool = getInspectionTool(shortName, element);
    if (tool != null) {
      var key = tool.getDefaultEditorAttributes();
      if (key != null) {
        return TextAttributesKey.find(key);
      }
    }
    return null;
  }

  boolean isExecutable(@Nullable Project project);

  /**
   * @see com.intellij.codeInspection.ex.InspectionProfileImpl#setSingleTool(String)
   *
   * @return tool short name when inspection profile corresponds to synthetic profile for single inspection run
   */
  @Nullable String getSingleTool();

  @NotNull String getDisplayName();

  @NotNull @Unmodifiable List<Tools> getAllEnabledInspectionTools(Project project);
}
