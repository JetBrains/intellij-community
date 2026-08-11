// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.performanceTests;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * This service helps tests to wait till full initialization and indexing of a project.
 * To ensure tests will wait till some particular async algorithm finishes, make sure that while it's running,
 * there always is some activity, which is already registered
 * with {@link ProjectInitializationDiagnostic#registerTracker(Project, String)},
 * but not yet finished with {@link ActivityTracker#activityFinished()}.
 * </p>
 * One can register activity in the very beginning and finish in the end,
 * or split algorithm into intersecting parts, and wrap them into activities separately.
 */
public final class ProjectInitializationDiagnostic {

  private ProjectInitializationDiagnostic() { }

  public static @NotNull ActivityTracker registerTracker(@NotNull Project project, @NotNull @NlsSafe String debugActivityName) {
    final var trackers = new ArrayList<ActivityTracker>();
    ProjectInitializationDiagnosticHandler.Companion.getEP_NAME().forEachExtensionSafe(ext -> {
      var tracker = ext.registerBeginningOfInitializationActivity(project, () -> debugActivityName);
      trackers.add(tracker);
    });
    return new AggregatedActivityTracker(trackers);
  }

  public static boolean isProjectInitializationAndIndexingFinished(@NotNull Project project) {
    return ContainerUtil.and(ProjectInitializationDiagnosticHandler.Companion.getEP_NAME().getExtensionList(),
                             ext -> ext.isProjectInitializationAndIndexingFinished(project));
  }

  public interface ActivityTracker {
    void activityFinished();
  }
}
