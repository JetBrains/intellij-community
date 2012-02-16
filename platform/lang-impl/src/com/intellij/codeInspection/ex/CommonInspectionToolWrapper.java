/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionNode;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public class CommonInspectionToolWrapper extends InspectionToolWrapper<InspectionTool, InspectionEP> {
  public CommonInspectionToolWrapper(InspectionEP ep) {
    super(ep);
  }

  CommonInspectionToolWrapper(InspectionTool tool) {
    super(tool);
  }

  CommonInspectionToolWrapper(InspectionEP ep, InspectionTool tool) {
    super(ep, tool);
  }


  @Override
  public CommonInspectionToolWrapper createCopy(InspectionToolWrapper<InspectionTool, InspectionEP> from) {
    return new CommonInspectionToolWrapper(from.myEP, from.myTool);
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope, @NotNull InspectionManager manager) {
    getTool().runInspection(scope, manager);
  }

  @Override
  public void initialize(@NotNull GlobalInspectionContextImpl context) {
    getTool().initialize(context);
  }

  @NotNull
  @Override
  public JobDescriptor[] getJobDescriptors(GlobalInspectionContext globalInspectionContext) {
    return getTool().getJobDescriptors(globalInspectionContext);
  }

  @Override
  public boolean isGraphNeeded() {
    return getTool().isGraphNeeded();
  }

  @Override
  public void updateContent() {
    getTool().updateContent();
  }

  @Override
  public boolean hasReportedProblems() {
    return getTool().hasReportedProblems();
  }

 @Override
  public Map<String, Set<RefEntity>> getContent() {
    return getTool().getContent();
  }

  @Override
  public GlobalInspectionContextImpl getContext() {
    return getTool().getContext();
  }

  @Override
  public HTMLComposerImpl getComposer() {
    return getTool().getComposer();
  }

  @Override
  public QuickFixAction[] getQuickFixes(RefEntity[] refElements) {
    return getTool().getQuickFixes(refElements);
  }

  @Override
  public InspectionNode createToolNode(InspectionRVContentProvider provider,
                                       InspectionTreeNode parentNode,
                                       boolean showStructure) {
    return getTool().createToolNode(provider, parentNode, showStructure);
  }

  @Override
  public boolean queryExternalUsagesRequests(InspectionManager manager) {
    return getTool().queryExternalUsagesRequests(manager);
  }


}
