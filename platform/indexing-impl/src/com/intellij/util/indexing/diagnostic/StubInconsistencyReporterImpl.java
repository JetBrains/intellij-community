// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic;

import com.intellij.openapi.project.Project;
import com.intellij.psi.stubs.StubInconsistencyReporter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class StubInconsistencyReporterImpl implements StubInconsistencyReporter {
  @Override
  public void reportEnforcedStubInconsistency(@NotNull Project project,
                                              @NotNull StubInconsistencyReporter.SourceOfCheck reason,
                                              @NotNull EnforcedInconsistencyType enforcedInconsistencyType) {
    IndexStatisticGroup.reportEnforcedStubInconsistency(project, reason, enforcedInconsistencyType);
  }

  @Override
  public void reportStubInconsistency(@NotNull Project project,
                                      @NotNull StubInconsistencyReporter.SourceOfCheck reason,
                                      @NotNull InconsistencyType type,
                                      @Nullable EnforcedInconsistencyType enforcedInconsistencyType) {
    IndexStatisticGroup.reportStubInconsistency(project, reason, type, enforcedInconsistencyType);
  }
}