// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

public interface InspectListener {
  default void inspectionFinished(long durationMs, long threadId, int problemCount, InspectionToolWrapper<?, ?> tool, InspectionKind kind) {}

  default void activityFinished(long durationMs, long threadId, ActivityKind activityKind) {}

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
