// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmTypeParameter;
import com.intellij.lang.jvm.types.JvmSubstitutor;
import com.intellij.lang.jvm.types.JvmType;
import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@Experimental
public class PsiJvmSubstitutor implements JvmSubstitutor {

  private final @NotNull Project myProject;
  private final @NotNull PsiSubstitutor mySubstitutor;

  public PsiJvmSubstitutor(@NotNull Project project, @NotNull PsiSubstitutor substitutor) {
    myProject = project;
    mySubstitutor = substitutor;
  }

  @Override
  public @NotNull Collection<JvmTypeParameter> getTypeParameters() {
    return new SmartList<>(mySubstitutor.getSubstitutionMap().keySet());
  }

  @Override
  public @Nullable JvmType substitute(@NotNull JvmTypeParameter typeParameter) {
    JvmPsiConversionHelper helper = JvmPsiConversionHelper.getInstance(myProject);
    PsiTypeParameter psiTypeParameter = helper.convertTypeParameter(typeParameter);
    return mySubstitutor.substitute(psiTypeParameter);
  }

  public @NotNull PsiSubstitutor getPsiSubstitutor() {
    return mySubstitutor;
  }
}
