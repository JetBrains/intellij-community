// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.performanceTests;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * This service helps tests to wait till full initialization and indexing of a project.
 * To ensure tests will wait till some particular async algorithm finishes, make sure that while it's running,
 * there always is some activity, which is already registered
 * with {@link ProjectInitializationDiagnosticService#registerBeginningOfInitializationActivity(Supplier)},
 * but not yet finished with {@link ActivityTracker#activityFinished()}.
 * </p>
 * One can register activity in the very beginning and finish in the end,
 * or split algorithm into intersecting parts, and wrap them into activities separately.
 */
public interface ProjectInitializationDiagnosticService {

  static @NotNull ProjectInitializationDiagnosticService getInstance(@NotNull Project project) {
    return project.getService(ProjectInitializationDiagnosticService.class);
  }

  static @NotNull ActivityTracker registerTracker(@NotNull Project project, @NotNull @NlsSafe String debugActivityName) {
    return getInstance(project).registerBeginningOfInitializationActivity(() -> debugActivityName);
  }

  ActivityTracker registerBeginningOfInitializationActivity(@NotNull Supplier<@NotNull @NlsSafe String> debugMessageProducer);

  boolean isProjectInitializationAndIndexingFinished();

  interface ActivityTracker {
    void activityFinished();
  }
}
