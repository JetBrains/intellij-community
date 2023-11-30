// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.util.SynchronizedBidiMultiMap;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.ThreeState;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface InspectionToolResultExporter extends ProblemDescriptionsProcessor {
  @NotNull
  Project getProject();

  void updateContent();

  @NotNull
  Map<String, Set<RefEntity>> getContent();

  void exportResults(@NotNull Consumer<? super Element> resultConsumer,
                     @NotNull RefEntity refEntity,
                     @NotNull Predicate<? super CommonProblemDescriptor> isDescriptorExcluded);

  void exportResults(@NotNull Consumer<? super Element> resultConsumer,
                     @NotNull Predicate<? super RefEntity> isEntityExcluded,
                     @NotNull Predicate<? super CommonProblemDescriptor> isProblemExcluded);

  @NotNull InspectionToolWrapper<?,?> getToolWrapper();

  @NotNull
  SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> getProblemElements();

  @Nullable
  HighlightSeverity getSeverity(@NotNull RefElement element);

  static @NotNull HighlightSeverity getSeverity(@Nullable RefEntity entity,
                                                @Nullable PsiElement psiElement,
                                                @NotNull InspectionToolResultExporter presentation) {
    HighlightSeverity severity = null;
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(presentation.getProject()).getCurrentProfile();
    if (entity instanceof RefElement refElement) {
      severity = presentation.getSeverity(refElement);
    }
    if (severity == null) {
      String shortName = presentation.getToolWrapper().getShortName();
      severity = profile.getErrorLevel(HighlightDisplayKey.find(shortName), psiElement).getSeverity();
    }
    return severity;
  }

  boolean isExcluded(@NotNull CommonProblemDescriptor descriptor);

  boolean isExcluded(@NotNull RefEntity entity);

  void amnesty(@NotNull RefEntity element);

  void exclude(@NotNull RefEntity element);

  void amnesty(@NotNull CommonProblemDescriptor descriptor);

  void exclude(@NotNull CommonProblemDescriptor descriptor);

  void suppressProblem(@NotNull CommonProblemDescriptor descriptor);

  void suppressProblem(@NotNull RefEntity entity);

  void addProblemElement(@Nullable RefEntity refElement, boolean filterSuppressed, CommonProblemDescriptor @NotNull ... descriptions);

  @NotNull
  Collection<CommonProblemDescriptor> getProblemDescriptors();

  @NotNull
  ThreeState hasReportedProblems();

  @NotNull
  Collection<RefEntity> getResolvedElements();

  boolean isProblemResolved(@Nullable CommonProblemDescriptor descriptor);

  boolean isProblemResolved(@Nullable RefEntity entity);

  CommonProblemDescriptor @NotNull [] getResolvedProblems(@NotNull RefEntity entity);
}
