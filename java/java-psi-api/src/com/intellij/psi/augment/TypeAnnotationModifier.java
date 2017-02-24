/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.augment;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiType;
import com.intellij.psi.TypeAnnotationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Type annotations are ignored during inference process. When they are present on types which are bounds of the inference variables,
 * then the corresponding instantiations of inference variables would contain those type annotations.
 * If different bounds contain contradicting type annotations or type annotations on types repeat target type annotations,
 * it could be useful to ignore such annotations in the resulted instantiation.
 */
public abstract class TypeAnnotationModifier {
  public static final ExtensionPointName<TypeAnnotationModifier> EP_NAME = ExtensionPointName.create("com.intellij.lang.psiTypeAnnotationModifier");

  /**
   * Called when a new bound is added to an inference variable. Implementations may adjust boundType's annotations based on the inference variable's type annotations.

   * @param inferenceVariableType   target type
   * @param boundType               bound which annotations should be changed according to present annotations
   *                                and annotations on target type
   * @return provider based on modified annotations or null if no applicable annotations found
   */
  @Nullable
  public TypeAnnotationProvider boundAppeared(@NotNull PsiType inferenceVariableType, @NotNull PsiType boundType) {
    return null;
  }

  /**
   * Called when the inference decides to use the lower bound of a type variable as its final result.

   * @return provider based on modified annotations or null if no applicable annotations found
   */
  @Nullable
  public TypeAnnotationProvider modifyLowerBoundAnnotations(@NotNull PsiType lowerBound, @NotNull PsiType upperBound) {
    return null;
  }

}
