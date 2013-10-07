/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 */
public class PsiGraphInferenceHelper implements PsiInferenceHelper {
  private final PsiManager myManager;

  public PsiGraphInferenceHelper(PsiManager manager) {
    myManager = manager;
  }

  @Override
  public PsiType inferTypeForMethodTypeParameter(@NotNull PsiTypeParameter typeParameter,
                                                 @NotNull PsiParameter[] parameters,
                                                 @NotNull PsiExpression[] arguments,
                                                 @NotNull PsiSubstitutor partialSubstitutor,
                                                 @Nullable PsiElement parent,
                                                 @NotNull ParameterTypeInferencePolicy policy) {
    final InferenceSession inferenceSession =
      new InferenceSession(new PsiTypeParameter[]{typeParameter}, parameters, arguments, partialSubstitutor, parent, myManager);
    return inferenceSession.infer(parameters, arguments, parent).substitute(typeParameter);
  }

  @NotNull
  @Override
  public PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                           @NotNull PsiParameter[] parameters,
                                           @NotNull PsiExpression[] arguments,
                                           @NotNull PsiSubstitutor partialSubstitutor,
                                           @NotNull PsiElement parent,
                                           @NotNull ParameterTypeInferencePolicy policy,
                                           @NotNull LanguageLevel languageLevel) {
    if (typeParameters.length == 0) return partialSubstitutor;
    final InferenceSession inferenceSession =
      new InferenceSession(typeParameters, parameters, arguments, partialSubstitutor, parent, myManager);
    return inferenceSession.infer(parameters, arguments, parent);
  }

  @NotNull
  @Override
  public PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                           @NotNull PsiType[] leftTypes,
                                           @NotNull PsiType[] rightTypes,
                                           @NotNull LanguageLevel languageLevel) {
    if (typeParameters.length == 0) return PsiSubstitutor.EMPTY;
    return new InferenceSession(typeParameters, leftTypes, rightTypes, PsiSubstitutor.EMPTY, myManager).infer();
  }

  @Override
  public PsiType getSubstitutionForTypeParameter(PsiTypeParameter typeParam,
                                                 PsiType param,
                                                 PsiType arg,
                                                 boolean isContraVariantPosition,
                                                 LanguageLevel languageLevel) {
    if (arg == PsiType.VOID || param == PsiType.VOID) return PsiType.NULL;
    final PsiType[] leftTypes;
    final PsiType[] rightTypes;
    if (isContraVariantPosition) {
      leftTypes = new PsiType[] {param};
      rightTypes = new PsiType[]{arg};
    }
    else {
      leftTypes = new PsiType[] {arg};
      rightTypes = new PsiType[]{param};
    }
    final InferenceSession inferenceSession = new InferenceSession(new PsiTypeParameter[]{typeParam}, leftTypes, rightTypes, PsiSubstitutor.EMPTY, myManager);
    if (inferenceSession.isProperType(param) && inferenceSession.isProperType(arg)) {
      boolean proceed = false;
      for (PsiClassType classType : typeParam.getExtendsListTypes()) {
        if (!inferenceSession.isProperType(classType)) {
          proceed = true;
          break;
        }
      }
      if (!proceed) {
        return PsiType.NULL;
      }
    }
    final PsiSubstitutor substitutor = inferenceSession.infer();
    return substitutor.substitute(typeParam);
  }
}
