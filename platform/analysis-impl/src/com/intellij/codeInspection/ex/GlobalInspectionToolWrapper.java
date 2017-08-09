/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GlobalInspectionToolWrapper extends InspectionToolWrapper<GlobalInspectionTool, InspectionEP> {
  private static final Logger LOG = Logger.getInstance(GlobalInspectionToolWrapper.class);

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
    GlobalInspectionTool tool = getTool();
    JobDescriptor[] additionalJobs = ObjectUtils.notNull(tool.getAdditionalJobs(context), JobDescriptor.EMPTY_ARRAY);
    StdJobDescriptors stdJobDescriptors = context.getStdJobDescriptors();
    if (tool.isGraphNeeded()) {
      additionalJobs = additionalJobs.length == 0 ? stdJobDescriptors.BUILD_GRAPH_ONLY :
                       ArrayUtil.append(additionalJobs, stdJobDescriptors.BUILD_GRAPH);
    }
    if (tool instanceof GlobalSimpleInspectionTool) {
      // if we run e.g. just "Annotator" simple global tool then myJobDescriptors are empty but LOCAL_ANALYSIS is used from inspectFile()
      additionalJobs = additionalJobs.length == 0 ? stdJobDescriptors.LOCAL_ANALYSIS_ARRAY :
                       ArrayUtil.contains(stdJobDescriptors.LOCAL_ANALYSIS, additionalJobs) ? additionalJobs :
                       ArrayUtil.append(additionalJobs, stdJobDescriptors.LOCAL_ANALYSIS);
    }
    return additionalJobs;
  }

  public boolean worksInBatchModeOnly() {
    return getTool().worksInBatchModeOnly();
  }

  @Nullable
  public LocalInspectionToolWrapper getSharedLocalInspectionToolWrapper() {
    final LocalInspectionTool sharedTool = getTool().getSharedLocalInspectionTool();
    if (sharedTool == null) {
      LOG.assertTrue(!isCleanupTool(), "Global cleanup tool MUST have shared local tool");
      return null;
    }
    //noinspection TestOnlyProblems
    return new LocalInspectionToolWrapper(sharedTool){
      @Nullable
      @Override
      public String getLanguage() {
        return GlobalInspectionToolWrapper.this.getLanguage(); // inherit "language=" xml tag from the global inspection EP
      }
    };
  }
}
