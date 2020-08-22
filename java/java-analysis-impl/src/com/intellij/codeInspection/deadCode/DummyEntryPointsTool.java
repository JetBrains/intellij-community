// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.deadCode;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.ex.JobDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("InspectionDescriptionNotFoundInspection")
public class DummyEntryPointsTool extends UnusedDeclarationInspectionBase {
  public DummyEntryPointsTool() {
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope,
                            @NotNull InspectionManager manager,
                            @NotNull GlobalInspectionContext globalContext,
                            @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
  }

  @Override
  public JobDescriptor @Nullable [] getAdditionalJobs(@NotNull GlobalInspectionContext context) {
    return JobDescriptor.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @NotNull
  public String getShortName() {
    return "";
  }
}
