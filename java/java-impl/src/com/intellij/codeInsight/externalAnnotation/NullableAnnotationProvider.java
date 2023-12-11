// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

public final class NullableAnnotationProvider implements AnnotationProvider {
  @NotNull
  @Override
  public String getName(Project project) {
    return NullableNotNullManager.getInstance(project).getDefaultNullable();
  }

  @Override
  public boolean isAvailable(PsiModifierListOwner owner) {
    return AddAnnotationPsiFix.isNullabilityAnnotationApplicable(owner);
  }

  @Override
  public String @NotNull [] getAnnotationsToRemove(Project project) {
    return ArrayUtilRt.toStringArray(NullableNotNullManager.getInstance(project).getNotNulls());
  }
}
