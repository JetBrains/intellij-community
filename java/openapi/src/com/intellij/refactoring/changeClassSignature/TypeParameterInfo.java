// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeClassSignature;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiTypeParameter;

/**
 * @author dsl
 */
public interface TypeParameterInfo {
  String getName(PsiTypeParameter[] parameters);

  PsiTypeParameter getTypeParameter(PsiTypeParameter[] parameters, Project project);
}
