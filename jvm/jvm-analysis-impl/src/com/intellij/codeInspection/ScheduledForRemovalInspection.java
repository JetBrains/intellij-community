// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReference;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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

  @Override
  protected void createProblem(@NotNull PsiReference reference,
                               @NotNull PsiModifierListOwner annotatedTarget,
                               @NotNull List<PsiAnnotation> annotations,
                               @NotNull ProblemsHolder holder) {
    //TODO determine highlight severity like in MarkedForRemovalInspection?
    PsiAnnotation scheduledForRemoval = ContainerUtil.find(annotations, psiAnnotation -> psiAnnotation.hasQualifiedName(ANNOTATION_NAME));
    if (scheduledForRemoval != null) {
      String inVersion = AnnotationUtil.getDeclaredStringAttributeValue(scheduledForRemoval, "inVersion");
      String targetText = getPresentableText(annotatedTarget);
      String message;
      if (inVersion == null || inVersion.isEmpty()) {
        message = JvmAnalysisBundle.message("jvm.inspections.scheduled.for.removal.description.no.version", targetText);
      } else {
        message = JvmAnalysisBundle.message("jvm.inspections.scheduled.for.removal.description.with.version", targetText, inVersion);
      }
      holder.registerProblem(reference, message, ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL);
    }
  }
}
