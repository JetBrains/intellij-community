// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public interface AnnotationProvider {
  ExtensionPointName<AnnotationProvider> KEY = ExtensionPointName.create("com.intellij.java.externalAnnotation");

  @NotNull
  String getName(Project project);

  boolean isAvailable(PsiModifierListOwner owner);

  @NotNull
  default String[] getAnnotationsToRemove(Project project) {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }
}
