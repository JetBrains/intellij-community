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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.ui.InspectionNode;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.vcs.FileStatus;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class CommonInspectionToolWrapper extends InspectionToolWrapper<InspectionTool, InspectionEP> {
  public CommonInspectionToolWrapper(@NotNull InspectionEP ep) {
    super(ep);
  }

  public CommonInspectionToolWrapper(@NotNull InspectionTool tool) {
    super(tool);
    assert !(tool instanceof InspectionToolWrapper);
  }

  private CommonInspectionToolWrapper(@NotNull CommonInspectionToolWrapper other) {
    super(other);
  }

  @NotNull
  @Override
  public CommonInspectionToolWrapper createCopy() {
    return new CommonInspectionToolWrapper(this);
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope, @NotNull InspectionManager manager) {
    getTool().runInspection(scope, manager);
  }

  @NotNull
  @Override
  public RefManager getRefManager() {
    return getTool().getRefManager();
  }

  @Override
  public void initialize(@NotNull GlobalInspectionContextImpl context) {
    getTool().initialize(context);
  }

  @NotNull
  @Override
  public JobDescriptor[] getJobDescriptors(@NotNull GlobalInspectionContext globalInspectionContext) {
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

  @NotNull
  @Override
  public GlobalInspectionContextImpl getContext() {
    return getTool().getContext();
  }

  @NotNull
  @Override
  public HTMLComposerImpl getComposer() {
    return getTool().getComposer();
  }

  @Override
  public void finalCleanup() {
    getTool().finalCleanup();
  }

  @Override
  public void cleanup() {
    getTool().cleanup();
  }

  @Nullable
  @Override
  public QuickFixAction[] getQuickFixes(@NotNull RefEntity[] refElements) {
    return getTool().getQuickFixes(refElements);
  }

  @NotNull
  @Override
  public InspectionNode createToolNode(@NotNull InspectionRVContentProvider provider,
                                       @NotNull InspectionTreeNode parentNode,
                                       boolean showStructure) {
    return getTool().createToolNode(provider, parentNode, showStructure);
  }

  @Override
  @SuppressWarnings({"UnusedDeclaration"})
  @Nullable
  public IntentionAction findQuickFixes(CommonProblemDescriptor descriptor, String hint) {
    return getTool().findQuickFixes(descriptor, hint);
  }

  @Override
  public HighlightSeverity getCurrentSeverity(@NotNull RefElement element) {
    return getTool().getCurrentSeverity(element);
  }

  @NotNull
  @Override
  public FileStatus getElementStatus(RefEntity element) {
    return getTool().getElementStatus(element);
  }

  @Override
  public boolean isElementIgnored(RefEntity element) {
    return getTool().isElementIgnored(element);
  }

  @Override
  @Nullable
  public Set<RefModule> getModuleProblems() {
    return getTool().getModuleProblems();
  }

  @Override
  public boolean isOldProblemsIncluded() {
    return getTool().isOldProblemsIncluded();
  }

  @Override
  @Nullable
  public Map<String, Set<RefEntity>> getOldContent() {
    return getTool().getOldContent();
  }

  @Override
  public boolean queryExternalUsagesRequests(@NotNull InspectionManager manager) {
    return getTool().queryExternalUsagesRequests(manager);
  }

  @Override
  public void exportResults(@NotNull Element parentNode, @NotNull RefEntity refEntity) {
    getTool().exportResults(parentNode, refEntity);
  }

  @Override
  public void exportResults(@NotNull Element parentNode) {
    getTool().exportResults(parentNode);
  }

  @Override
  public void amnesty(RefEntity refEntity) {
    getTool().amnesty(refEntity);
  }

  @Override
  public void ignoreCurrentElement(RefEntity refElement) {
    getTool().ignoreCurrentElement(refElement);
  }

  @NotNull
  @Override
  public Collection<RefEntity> getIgnoredRefElements() {
    return getTool().getIgnoredRefElements();
  }

  @Override
  @Nullable
  public SuppressIntentionAction[] getSuppressActions() {
    return getTool().getSuppressActions();
  }
}
