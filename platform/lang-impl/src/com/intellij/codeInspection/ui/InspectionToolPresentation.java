// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.InspectionToolResultExporter;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface InspectionToolPresentation extends InspectionToolResultExporter {
  @Override
  @NotNull
  InspectionToolWrapper<?,?> getToolWrapper();

  @ApiStatus.Internal
  default void patchToolNode(@NotNull InspectionTreeNode node,
                             @NotNull InspectionRVContentProvider provider,
                             boolean showStructure,
                             boolean groupBySeverity) {

  }

  @ApiStatus.Internal
  default @NotNull RefElementNode createRefNode(@Nullable RefEntity entity,
                                                @NotNull InspectionTreeModel model,
                                                @NotNull InspectionTreeNode parent) {
    return new RefElementNode(entity, this, parent);
  }

  void cleanup();
  @Nullable
  QuickFix<?> findQuickFixes(@NotNull CommonProblemDescriptor descriptor, RefEntity entity, String hint);
  @NotNull
  HTMLComposerImpl getComposer();

  QuickFixAction @NotNull [] getQuickFixes(RefEntity @NotNull ... refElements);

  @NotNull
  GlobalInspectionContextImpl getContext();

  /** Override the preview panel for the inspection node. */
  @ApiStatus.Internal
  default @Nullable JComponent getCustomPreviewPanel(@NotNull InspectionNode inspectionNode) {
    return null;
  }

  /** Override the preview panel for the module node. */
  @ApiStatus.Internal
  default @Nullable JComponent getCustomPreviewPanel(@NotNull InspectionModuleNode moduleNode) {
    return null;
  }

  /** Override the preview panel for the package node. */
  @ApiStatus.Internal
  default @Nullable JComponent getCustomPreviewPanel(@NotNull InspectionPackageNode packageNode) {
    return null;
  }

  /** Override the preview panel for the entity. */
  default @Nullable JComponent getCustomPreviewPanel(@NotNull RefEntity entity) {
    return null;
  }

  /** Override the preview panel for the problem descriptor. */
  default @Nullable JComponent getCustomPreviewPanel(@NotNull CommonProblemDescriptor descriptor, @NotNull Disposable parent) {
    return null;
  }

  /** Additional actions applicable to the inspection node. May be (but not necessarily) related to the custom preview panel. */
  @ApiStatus.Internal
  default @Nullable JComponent getCustomActionsPanel(@NotNull InspectionNode inspectionNode) {
    return null;
  }

  /** Additional actions applicable to the module node. May be (but not necessarily) related to the custom preview panel. */
  @ApiStatus.Internal
  default @Nullable JComponent getCustomActionsPanel(@NotNull InspectionModuleNode moduleNode) {
    return null;
  }

  /** Additional actions applicable to the package node. May be (but not necessarily) related to the custom preview panel. */
  @ApiStatus.Internal
  default @Nullable JComponent getCustomActionsPanel(@NotNull InspectionPackageNode packageNode) {
    return null;
  }

  /** Additional actions applicable to the entity. May be (but not necessarily) related to the custom preview panel. */
  @ApiStatus.Internal
  default @Nullable JComponent getCustomActionsPanel(@NotNull RefEntity entity) {
    return null;
  }

  /** Additional actions applicable to the problem descriptor. May be (but not necessarily) related to the custom preview panel. */
  default @Nullable JComponent getCustomActionsPanel(@NotNull CommonProblemDescriptor descriptor, @NotNull Disposable parent) {
    return null;
  }

  /**
   * Provides a custom toolbar for the fix/action buttons area.
   * Called regardless of whether there are problems selected (exclusion-resistant).
   * Results view fallbacks to the default behavior if null is returned.
   */
  @ApiStatus.Internal
  default @Nullable JComponent getCustomToolbar(@NotNull InspectionResultsView view) {
    return null;
  }

  /**
   * @return true iff custom actions panel should be aligned to the left and
   * fix toolbar to the right
   */
  default boolean shouldAlignCustomActionPanelToLeft() { return false; }

  /**
   * see {@link com.intellij.codeInspection.deadCode.DummyEntryPointsPresentation}
   * @return false only if contained problem elements contain real highlighted problem in code.
   */
  default boolean isDummy() {
    return false;
  }

  default boolean showProblemCount() {
    return true;
  }

  boolean isSuppressed(RefEntity element);

  boolean isSuppressed(CommonProblemDescriptor descriptor);

  CommonProblemDescriptor @NotNull [] getSuppressedProblems(@NotNull RefEntity entity);
}
