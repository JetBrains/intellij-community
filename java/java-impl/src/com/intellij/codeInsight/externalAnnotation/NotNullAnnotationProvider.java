// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.externalAnnotation;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

public final class NotNullAnnotationProvider implements AnnotationProvider {
  @Override
  public @NotNull String getName(Project project) {
    return NullableNotNullManager.getInstance(project).getDefaultNotNull();
  }

  @Override
  public boolean isAvailable(PsiModifierListOwner owner) {
    return AddAnnotationPsiFix.isNullabilityAnnotationApplicable(owner);
  }

  @Override
  public String @NotNull [] getAnnotationsToRemove(Project project) {
    return ArrayUtilRt.toStringArray(NullableNotNullManager.getInstance(project).getNullables());
  }
}
