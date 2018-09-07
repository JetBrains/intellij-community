// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public class NullableAnnotationProvider implements AnnotationProvider {
  @NotNull
  @Override
  public String getName(Project project) {
    return NullableNotNullManager.getInstance(project).getDefaultNullable();
  }

  @Override
  public boolean isAvailable(PsiModifierListOwner owner) {
    return AddAnnotationPsiFix.isNullabilityAnnotationApplicable(owner);
  }

  @NotNull
  @Override
  public String[] getAnnotationsToRemove(Project project) {
    return NullableNotNullManager.getInstance(project).getNotNulls().toArray(ArrayUtil.EMPTY_STRING_ARRAY);
  }
}
