// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation;

import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

public interface AnnotationProvider {
  ExtensionPointName<AnnotationProvider> KEY = ExtensionPointName.create("com.intellij.java.externalAnnotation");

  @NotNull
  @NlsSafe
  String getName(Project project);

  boolean isAvailable(PsiModifierListOwner owner);

  default String @NotNull [] getAnnotationsToRemove(Project project) {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @NotNull
  default AddAnnotationFix createFix(@NotNull PsiModifierListOwner owner) {
    Project project = owner.getProject();
    return new AddAnnotationFix(getName(project), owner, getAnnotationsToRemove(project));
  }
}
