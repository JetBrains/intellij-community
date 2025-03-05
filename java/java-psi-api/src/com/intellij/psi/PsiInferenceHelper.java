// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.infos.MethodCandidateInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PsiInferenceHelper {
  /**
   * @return {@link PsiTypes#nullType()} iff no type could be inferred
   *         null         iff the type inferred is raw
   *         inferred type otherwise
   */
  PsiType inferTypeForMethodTypeParameter(@NotNull PsiTypeParameter typeParameter,
                                          PsiParameter @NotNull [] parameters,
                                          PsiExpression @NotNull [] arguments,
                                          @NotNull PsiSubstitutor partialSubstitutor,
                                          @Nullable PsiElement parent,
                                          @NotNull ParameterTypeInferencePolicy policy);

  @NotNull
  PsiSubstitutor inferTypeArguments(PsiTypeParameter @NotNull [] typeParameters,
                                    PsiParameter @NotNull [] parameters,
                                    PsiExpression @NotNull [] arguments,
                                    @Nullable MethodCandidateInfo currentMethod,
                                    @NotNull PsiSubstitutor partialSubstitutor,
                                    @NotNull PsiElement parent,
                                    @NotNull ParameterTypeInferencePolicy policy,
                                    @NotNull LanguageLevel languageLevel);
  @NotNull
  PsiSubstitutor inferTypeArguments(PsiTypeParameter @NotNull [] typeParameters,
                                    PsiType @NotNull [] leftTypes,
                                    PsiType @NotNull [] rightTypes,
                                    @NotNull LanguageLevel languageLevel);


  default @NotNull PsiSubstitutor inferTypeArguments(PsiTypeParameter @NotNull [] typeParameters,
                                                     PsiType @NotNull [] leftTypes,
                                                     PsiType @NotNull [] rightTypes,
                                                     @NotNull PsiSubstitutor partialSubstitutor,
                                                     @NotNull LanguageLevel languageLevel){
    return inferTypeArguments(typeParameters, leftTypes, rightTypes, languageLevel);
  }

  PsiType getSubstitutionForTypeParameter(PsiTypeParameter typeParam,
                                          PsiType param,
                                          PsiType arg,
                                          boolean isContraVariantPosition,
                                          LanguageLevel languageLevel);
}
