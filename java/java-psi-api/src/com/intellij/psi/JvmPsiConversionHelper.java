// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmMethod;
import com.intellij.lang.jvm.JvmTypeDeclaration;
import com.intellij.lang.jvm.JvmTypeParameter;
import com.intellij.lang.jvm.types.JvmSubstitutor;
import com.intellij.lang.jvm.types.JvmType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Experimental
public interface JvmPsiConversionHelper {

  @NotNull
  static JvmPsiConversionHelper getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, JvmPsiConversionHelper.class);
  }

  @Nullable
  PsiClass convertTypeDeclaration(@Nullable JvmTypeDeclaration typeDeclaration);

  @NotNull
  PsiTypeParameter convertTypeParameter(@NotNull JvmTypeParameter typeParameter);

  @NotNull
  PsiType convertType(@NotNull JvmType type);

  @NotNull
  PsiSubstitutor convertSubstitutor(@NotNull JvmSubstitutor substitutor);

  @NotNull
  PsiMethod convertMethod(@NotNull JvmMethod method);
}
