// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;

import java.util.Collections;
import java.util.List;

//TODO quickfix like in deprecation inspection?
public class ScheduledForRemovalInspection extends AnnotatedElementInspectionBase {

  private static final String ANNOTATION_NAME = ApiStatus.ScheduledForRemoval.class.getCanonicalName();

  @NotNull
  @Override
  protected List<String> getAnnotations() {
    return Collections.singletonList(ANNOTATION_NAME);
  }

  @NotNull
  @Override
  protected AnnotatedApiUsageProcessor buildAnnotatedApiUsageProcessor(@NotNull ProblemsHolder holder) {
    return new AnnotatedApiUsageProcessor() {
      @Override
      public void processAnnotatedTarget(@NotNull UElement sourceNode,
                                         @NotNull PsiModifierListOwner annotatedTarget,
                                         @NotNull List<? extends PsiAnnotation> annotations) {
        if (!AnnotatedElementInspectionBase.isLibraryElement(annotatedTarget)) {
          return;
        }
        //TODO determine highlight severity like in MarkedForRemovalInspection?
        PsiElement elementToHighlight = sourceNode.getSourcePsi();
        PsiAnnotation scheduledForRemoval = ContainerUtil.find(
          annotations, psiAnnotation -> psiAnnotation.hasQualifiedName(ANNOTATION_NAME)
        );
        if (elementToHighlight != null && scheduledForRemoval != null) {
          String inVersion = AnnotationUtil.getDeclaredStringAttributeValue(scheduledForRemoval, "inVersion");
          String targetText = getPresentableText(annotatedTarget);
          String message;
          if (inVersion == null || inVersion.isEmpty()) {
            message = JvmAnalysisBundle.message("jvm.inspections.scheduled.for.removal.description.no.version", targetText);
          }
          else {
            message = JvmAnalysisBundle.message("jvm.inspections.scheduled.for.removal.description.with.version", targetText, inVersion);
          }
          holder.registerProblem(elementToHighlight, message, ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL);
        }
      }
    };
  }
}
