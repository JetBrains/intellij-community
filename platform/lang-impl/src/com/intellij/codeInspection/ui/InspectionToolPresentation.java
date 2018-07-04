/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  boolean isProblemResolved(@Nullable CommonProblemDescriptor descriptor);

  boolean isProblemResolved(@Nullable RefEntity entity);

  @NotNull
  Collection<RefEntity> getResolvedElements();

  @NotNull
  CommonProblemDescriptor[] getResolvedProblems(@NotNull RefEntity entity);

  void suppressProblem(@NotNull CommonProblemDescriptor descriptor);

  void suppressProblem(@NotNull RefEntity entity);

  boolean isSuppressed(RefEntity element);

  boolean isSuppressed(CommonProblemDescriptor descriptor);

  @NotNull
  CommonProblemDescriptor[] getSuppressedProblems(@NotNull RefEntity entity);

  void cleanup();
  @Nullable
  IntentionAction findQuickFixes(@NotNull CommonProblemDescriptor descriptor, final String hint);
  @NotNull
  HTMLComposerImpl getComposer();
  void exportResults(@NotNull final Element parentNode, @NotNull RefEntity refEntity, Predicate<CommonProblemDescriptor> isDescriptorExcluded);
  @NotNull
  QuickFixAction[] getQuickFixes(@NotNull RefEntity... refElements);
  @NotNull
  SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> getProblemElements();
  @NotNull
  Collection<CommonProblemDescriptor> getProblemDescriptors();
  void addProblemElement(@Nullable RefEntity refElement, boolean filterSuppressed, @NotNull CommonProblemDescriptor... descriptions);

  @NotNull
  GlobalInspectionContextImpl getContext();

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
