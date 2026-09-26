// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Extension point to listen for inspection problems being added to the results.
 * Allows plugins to react when new problems are found during inspection
 * (e.g., to start background analysis immediately).
 * <p>
 * This listener is called at the analysis level, independent of UI.
 */
@ApiStatus.Experimental
public interface InspectionProblemAddedListener {
  ExtensionPointName<InspectionProblemAddedListener> EP_NAME =
    ExtensionPointName.create("com.intellij.inspectionProblemAddedListener");

  /**
   * Called when new problem descriptors are added to the inspection results.
   *
   * @param project     the current project
   * @param toolWrapper the inspection tool that found the problems
   * @param descriptors the problem descriptors being added
   */
  void onProblemsAdded(
    @NotNull Project project,
    @NotNull InspectionToolWrapper<?, ?> toolWrapper,
    CommonProblemDescriptor @NotNull [] descriptors
  );
}
