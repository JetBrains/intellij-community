// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

//TODO quickfix like in deprecation inspection?
public class ScheduledForRemovalInspection extends AnnotatedElementInspectionBase {
  @NotNull
  @Override
  protected List<String> getAnnotations() {
    return Collections.singletonList(ApiStatus.ScheduledForRemoval.class.getCanonicalName());
  }

  @Override
  protected void createProblem(@NotNull PsiReference reference, @NotNull ProblemsHolder holder) {
    //TODO determine highlight severity like in MarkedForRemovalInspection (and extend the description)?
    String message = JvmAnalysisBundle.message("jvm.inspections.scheduled.for.removal.description", getReferenceText(reference));
    holder.registerProblem(reference, message, ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL);
  }
}
