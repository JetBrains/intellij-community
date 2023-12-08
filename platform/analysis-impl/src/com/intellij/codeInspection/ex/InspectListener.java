// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public interface InspectListener {
  default void inspectionFinished(long duration, long threadId, int problemsCount, InspectionToolWrapper<?, ?> tool, InspectionKind kind,
                                  @Nullable PsiFile file, Project project) {}

  default void activityFinished(long duration, long threadId, ActivityKind activityKind, Project project) {}

  default void fileAnalyzed(PsiFile file, Project project) {}

  default void inspectionFailed(String toolId, Throwable throwable, @Nullable PsiFile file, Project project) { }

  enum InspectionKind {
    LOCAL,
    LOCAL_PRIORITY,
    GLOBAL_SIMPLE,
    GLOBAL
  }

  enum ActivityKind {
    REFERENCE_SEARCH,
    GLOBAL_POST_RUN_ACTIVITIES,
    EXTERNAL_TOOLS_CONFIGURATION,
    EXTERNAL_TOOLS_EXECUTION
  }
}
