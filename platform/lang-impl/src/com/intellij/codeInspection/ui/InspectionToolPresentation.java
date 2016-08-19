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
package com.intellij.codeInspection.ui;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.openapi.vcs.FileStatus;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public interface InspectionToolPresentation extends ProblemDescriptionsProcessor {

  @NotNull
  InspectionToolWrapper getToolWrapper();

  @NotNull
  Map<RefEntity, CommonProblemDescriptor[]> getIgnoredElements();

  @NotNull
  InspectionNode createToolNode(@NotNull GlobalInspectionContextImpl globalInspectionContext,
                                @NotNull InspectionNode node,
                                @NotNull InspectionRVContentProvider provider,
                                @NotNull InspectionTreeNode parentNode,
                                final boolean showStructure,
                                final boolean groupBySeverity);

  @NotNull
  default RefElementNode createRefNode(@Nullable RefEntity entity) {
    return new RefElementNode(entity, this);
  }

  void updateContent();

  boolean hasReportedProblems();

  @NotNull
  Map<String, Set<RefEntity>> getContent();

  void ignoreCurrentElement(RefEntity refEntity);
  void amnesty(RefEntity refEntity);
  void amnesty(RefEntity refEntity, CommonProblemDescriptor descriptor);
  void cleanup();
  void finalCleanup();
  boolean isGraphNeeded();
  boolean isElementIgnored(final RefEntity element);
  @NotNull
  FileStatus getElementStatus(final RefEntity element);
  @NotNull
  Set<RefEntity> getIgnoredRefElements();
  @Nullable
  IntentionAction findQuickFixes(@NotNull CommonProblemDescriptor descriptor, final String hint);
  @NotNull
  HTMLComposerImpl getComposer();
  void exportResults(@NotNull final Element parentNode, @NotNull RefEntity refEntity, Predicate<CommonProblemDescriptor> isDescriptorExcluded);
  @NotNull
  Set<RefModule> getModuleProblems();
  @Nullable
  QuickFixAction[] getQuickFixes(@NotNull final RefEntity[] refElements, @Nullable CommonProblemDescriptor[] descriptors);
  @NotNull
  Map<RefEntity, CommonProblemDescriptor[]> getProblemElements();
  @NotNull
  Collection<CommonProblemDescriptor> getProblemDescriptors();
  @NotNull
  FileStatus getProblemStatus(@NotNull CommonProblemDescriptor descriptor);
  boolean isProblemResolved(RefEntity refEntity, CommonProblemDescriptor descriptor);
  void ignoreCurrentElementProblem(RefEntity refEntity, CommonProblemDescriptor descriptor);
  void addProblemElement(RefEntity refElement, boolean filterSuppressed, @NotNull CommonProblemDescriptor... descriptions);
  void ignoreProblem(@NotNull CommonProblemDescriptor descriptor, @NotNull QuickFix fix);

  @NotNull
  GlobalInspectionContextImpl getContext();
  void ignoreProblem(RefEntity refEntity, CommonProblemDescriptor problem, int idx);
  @Nullable
  QuickFixAction[] extractActiveFixes(@NotNull RefEntity[] refElements,
                                      @NotNull Map<RefEntity, CommonProblemDescriptor[]> descriptorMap,
                                      @Nullable CommonProblemDescriptor[] allowedDescriptors);
  void exportResults(@NotNull final Element parentNode,
                     @NotNull final Predicate<RefEntity> isEntityExcluded,
                     @NotNull final Predicate<CommonProblemDescriptor> isProblemExcluded);

  default JComponent getCustomPreviewPanel(RefEntity entity) {
    return null;
  };

  /**
   * see {@link com.intellij.codeInspection.deadCode.DummyEntryPointsPresentation}
   * @return false only if contained problem elements contain real highlighted problem in code.
   */
  default boolean isDummy() {
    return false;
  }
}
