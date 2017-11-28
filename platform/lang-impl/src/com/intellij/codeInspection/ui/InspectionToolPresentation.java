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
package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.ui.util.SynchronizedBidiMultiMap;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public interface InspectionToolPresentation extends ProblemDescriptionsProcessor {

  @NotNull
  InspectionToolWrapper getToolWrapper();

  void createToolNode(@NotNull GlobalInspectionContextImpl globalInspectionContext,
                      @NotNull InspectionNode node,
                      @NotNull InspectionRVContentProvider provider,
                      @NotNull InspectionTreeNode parentNode,
                      boolean showStructure,
                      boolean groupBySeverity);

  @Nullable
  InspectionNode getToolNode();

  @NotNull
  default RefElementNode createRefNode(@Nullable RefEntity entity) {
    return new RefElementNode(entity, this);
  }

  void updateContent();

  boolean hasReportedProblems();

  @NotNull
  Map<String, Set<RefEntity>> getContent();

  void resolveProblem(@NotNull CommonProblemDescriptor descriptor);

  boolean isProblemResolved(@Nullable CommonProblemDescriptor descriptor);

  boolean isProblemResolved(@Nullable RefEntity entity);

  @NotNull
  Collection<RefEntity> getResolvedElements();

  void suppressProblem(@NotNull CommonProblemDescriptor descriptor);

  void suppressProblem(@NotNull RefEntity entity);

  boolean isSuppressed(RefEntity element);

  boolean isSuppressed(CommonProblemDescriptor descriptor);

  void cleanup();
  @Nullable
  IntentionAction findQuickFixes(@NotNull CommonProblemDescriptor descriptor, final String hint);
  @NotNull
  HTMLComposerImpl getComposer();
  void exportResults(@NotNull final Element parentNode, @NotNull RefEntity refEntity, Predicate<CommonProblemDescriptor> isDescriptorExcluded);
  @NotNull
  Set<RefModule> getModuleProblems();
  @NotNull
  QuickFixAction[] getQuickFixes(@NotNull final RefEntity[] refElements, @Nullable InspectionTree tree);
  @NotNull
  SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> getProblemElements();
  @NotNull
  Collection<CommonProblemDescriptor> getProblemDescriptors();
  void addProblemElement(@Nullable RefEntity refElement, boolean filterSuppressed, @NotNull CommonProblemDescriptor... descriptions);

  @NotNull
  GlobalInspectionContextImpl getContext();
  @NotNull
  QuickFixAction[] extractActiveFixes(@NotNull RefEntity[] refElements,
                                      @NotNull Function<RefEntity, CommonProblemDescriptor[]> descriptorMap,
                                      @Nullable CommonProblemDescriptor[] allowedDescriptors);
  void exportResults(@NotNull Element parentNode,
                     @NotNull Predicate<RefEntity> isEntityExcluded,
                     @NotNull Predicate<CommonProblemDescriptor> isProblemExcluded);

  @Nullable
  default JComponent getCustomPreviewPanel(@NotNull RefEntity entity) {
    return null;
  }

  /**
   * see {@link com.intellij.codeInspection.deadCode.DummyEntryPointsPresentation}
   * @return false only if contained problem elements contain real highlighted problem in code.
   */
  default boolean isDummy() {
    return false;
  }

  default int getProblemsCount(@NotNull InspectionTree tree) {
    return tree.getSelectedDescriptors().length;
  }

  @Nullable
  HighlightSeverity getSeverity(@NotNull RefElement element);

  boolean isExcluded(@NotNull CommonProblemDescriptor descriptor);

  boolean isExcluded(@NotNull RefEntity entity);

  void amnesty(@NotNull RefEntity element);

  void exclude(@NotNull RefEntity element);

  void amnesty(@NotNull CommonProblemDescriptor descriptor);

  void exclude(@NotNull CommonProblemDescriptor descriptor);

  static HighlightSeverity getSeverity(@Nullable RefEntity entity,
                                       @Nullable PsiElement psiElement,
                                       @NotNull InspectionToolPresentation presentation) {
    HighlightSeverity severity;
    if (entity instanceof RefElement){
      final RefElement refElement = (RefElement)entity;
      severity = presentation.getSeverity(refElement);
    }
    else {
      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(presentation.getContext().getProject()).getCurrentProfile();
      final HighlightDisplayLevel
        level = profile.getErrorLevel(HighlightDisplayKey.find(presentation.getToolWrapper().getShortName()), psiElement);
      severity = level.getSeverity();
    }
    return severity;
  }
}
