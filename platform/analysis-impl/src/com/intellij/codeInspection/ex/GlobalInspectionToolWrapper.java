/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: 28-Dec-2005
 */
public class GlobalInspectionToolWrapper extends InspectionToolWrapper<GlobalInspectionTool, InspectionEP> {
  public GlobalInspectionToolWrapper(@NotNull GlobalInspectionTool globalInspectionTool) {
    super(globalInspectionTool);
  }

  public GlobalInspectionToolWrapper(@NotNull GlobalInspectionTool tool, @NotNull InspectionEP ep) {
    super(tool, ep);
  }

  public GlobalInspectionToolWrapper(@NotNull InspectionEP ep) {
    super(ep);
  }

  private GlobalInspectionToolWrapper(@NotNull GlobalInspectionToolWrapper other) {
    super(other);
  }

  @NotNull
  @Override
  public GlobalInspectionToolWrapper createCopy() {
    return new GlobalInspectionToolWrapper(this);
  }

  @Override
  public void initialize(@NotNull GlobalInspectionContext context) {
    super.initialize(context);
    RefManagerImpl refManager = (RefManagerImpl)context.getRefManager();
    final RefGraphAnnotator annotator = getTool().getAnnotator(refManager);
    if (annotator != null) {
      refManager.registerGraphAnnotator(annotator);
    }
    getTool().initialize(context);
  }

  @Override
  @NotNull
  public JobDescriptor[] getJobDescriptors(@NotNull GlobalInspectionContext context) {
    final JobDescriptor[] additionalJobs = getTool().getAdditionalJobs();
    if (additionalJobs == null) {
      return getTool().isGraphNeeded() ? context.getStdJobDescriptors().BUILD_GRAPH_ONLY : JobDescriptor.EMPTY_ARRAY;
    }
    else {
      return getTool().isGraphNeeded() ? ArrayUtil.append(additionalJobs, context.getStdJobDescriptors().BUILD_GRAPH) : additionalJobs;
    }
  }

  public boolean worksInBatchModeOnly() {
    return getTool().worksInBatchModeOnly();
  }

  @Nullable
  public LocalInspectionToolWrapper getSharedLocalInspectionToolWrapper() {
    final LocalInspectionTool sharedTool = getTool().getSharedLocalInspectionTool();
    if (sharedTool == null) {
      return null;
    }
    return new LocalInspectionToolWrapper(sharedTool);
  }
}
