// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation;

import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;

public final class DeprecationAnnotationProvider implements AnnotationProvider {
  @NotNull
  @Override
  public String getName(Project project) {
    return CommonClassNames.JAVA_LANG_DEPRECATED;
  }

  @Override
  public boolean isAvailable(PsiModifierListOwner owner) {
    return true;
  }
}
