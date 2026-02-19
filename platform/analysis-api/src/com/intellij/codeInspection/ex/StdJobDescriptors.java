// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisBundle;
import org.jetbrains.annotations.ApiStatus;

public final class StdJobDescriptors {
  public final JobDescriptor BUILD_GRAPH = new JobDescriptor(AnalysisBundle.message("inspection.processing.job.descriptor"));
  public final JobDescriptor[] BUILD_GRAPH_ONLY = {BUILD_GRAPH};
  public final JobDescriptor FIND_EXTERNAL_USAGES = new JobDescriptor(AnalysisBundle.message("inspection.processing.job.descriptor1"));
  @ApiStatus.Internal
  public final JobDescriptor LOCAL_ANALYSIS = new JobDescriptor(AnalysisBundle.message("inspection.processing.job.descriptor2"));
  public final JobDescriptor[] LOCAL_ANALYSIS_ARRAY = {LOCAL_ANALYSIS};
}
