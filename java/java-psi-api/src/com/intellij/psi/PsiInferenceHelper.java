// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PsiInferenceHelper {
  /**
   * @return {@link PsiType#NULL} iff no type could be inferred
   *         null         iff the type inferred is raw
   *         inferred type otherwise
   */
  PsiType inferTypeForMethodTypeParameter(@NotNull PsiTypeParameter typeParameter,
                                          @NotNull PsiParameter[] parameters,
                                          @NotNull PsiExpression[] arguments,
                                          @NotNull PsiSubstitutor partialSubstitutor,
                                          @Nullable PsiElement parent,
                                          @NotNull ParameterTypeInferencePolicy policy);

  @NotNull
  PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                    @NotNull PsiParameter[] parameters,
                                    @NotNull PsiExpression[] arguments,
                                    @NotNull PsiSubstitutor partialSubstitutor,
                                    @NotNull PsiElement parent,
                                    @NotNull ParameterTypeInferencePolicy policy,
                                    @NotNull LanguageLevel languageLevel);
  @NotNull
  PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                    @NotNull PsiType[] leftTypes,
                                    @NotNull PsiType[] rightTypes,
                                    @NotNull LanguageLevel languageLevel);


  @NotNull
  default PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                            @NotNull PsiType[] leftTypes,
                                            @NotNull PsiType[] rightTypes,
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
