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
  public void reportStubInconsistencyBetweenPsiAndText(@NotNull Project project,
                                                       @Nullable SourceOfCheck reason,
                                                       @NotNull InconsistencyType type) {
    StubInconsistencyReportUtil.reportStubInconsistencyBetweenPsiAndText(project, reason, type);
  }

  @Override
  public void reportEnforcedStubInconsistency(@NotNull Project project,
                                              @NotNull StubInconsistencyReporter.SourceOfCheck reason,
                                              @SuppressWarnings("deprecation") @NotNull EnforcedInconsistencyType enforcedInconsistencyType) {
    //ignore
  }

  @Override
  public void reportStubInconsistencyBetweenPsiAndText(@NotNull Project project,
                                                       @NotNull StubInconsistencyReporter.SourceOfCheck reason,
                                                       @NotNull InconsistencyType type,
                                                       @SuppressWarnings("deprecation") @Nullable EnforcedInconsistencyType enforcedInconsistencyType) {
    //ignore
  }

  @Override
  public void reportKotlinDescriptorNotFound(@Nullable Project project) {
    StubInconsistencyReportUtil.reportKotlinDescriptorNotFound(project);
  }

  @Override
  public void reportKotlinMissingClassName(@NotNull Project project,
                                           boolean foundInKotlinFullClassNameIndex,
                                           boolean foundInEverythingScope) {
    StubInconsistencyReportUtil.reportKotlinMissingClassName(project, foundInKotlinFullClassNameIndex, foundInEverythingScope);
  }

  @Override
  public void reportStubTreeAndIndexDoNotMatch(@NotNull Project project, @NotNull StubTreeAndIndexDoNotMatchSource source) {
    StubInconsistencyReportUtil.reportStubTreeAndIndexDoNotMatch(project, source);
  }
}