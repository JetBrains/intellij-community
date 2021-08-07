// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.openapi.project.Project;

public interface InspectListener {
  default void inspectionFinished(long duration, long threadId, int problemsCount, InspectionToolWrapper<?, ?> tool, InspectionKind kind,
                                  Project project) {}

  default void activityFinished(long duration, long threadId, ActivityKind activityKind, Project project) {}

  enum InspectionKind {
    LOCAL,
    LOCAL_PRIORITY,
    GLOBAL_SIMPLE,
    GLOBAL
  }

  enum ActivityKind {
    REFERENCE_SEARCH,
    GLOBAL_POST_RUN_ACTIVITIES
  }
}
