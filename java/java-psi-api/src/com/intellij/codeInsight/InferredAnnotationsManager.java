/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Returns annotations inferred by bytecode our source code, for example contracts and nullity.
 * 
 * @see com.intellij.codeInsight.NullableNotNullManager
 * @see org.jetbrains.annotations.Contract
 * @see org.jetbrains.annotations.Nullable
 * @see org.jetbrains.annotations.NotNull
 * @see com.intellij.codeInsight.AnnotationUtil
 */
public abstract class InferredAnnotationsManager {
  private static final NotNullLazyKey<InferredAnnotationsManager, Project> INSTANCE_KEY = ServiceManager.createLazyKey(InferredAnnotationsManager.class);

  public static InferredAnnotationsManager getInstance(@NotNull Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  /**
   * @return if exists, an inferred annotation by given qualified name on a given PSI element. Several invocations may return several 
   * different instances of {@link com.intellij.psi.PsiAnnotation}, which are not guaranteed to be equal.
   */
  @Nullable
  public abstract PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);

  /**
   * There is a number of well-known methods where automatic inference fails (for example, {@link java.util.Objects#requireNonNull(Object)}. 
   * For such methods, contracts are hardcoded, and for their parameters inferred @NotNull are suppressed.<p/>
   * 
   * In addition, package-default annotations like @ParametersAreNonnullByDefault are not honored for parameters where 
   * {@link org.jetbrains.annotations.NotNull} inference is ignored.
   * 
   * @return whether inference is to be suppressed the given annotation on the given method or parameter  
   */
  public abstract boolean ignoreInference(@NotNull PsiModifierListOwner owner, @Nullable String annotationFQN);

  /**
   * When annotation name is known, prefer {@link #findInferredAnnotation(com.intellij.psi.PsiModifierListOwner, String)} as
   * potentially faster.
   * 
   * @return all inferred annotations for the given element
   */
  @NotNull
  public abstract PsiAnnotation[] findInferredAnnotations(@NotNull PsiModifierListOwner listOwner);

  /**
   * @return whether the given annotation was inferred by this service.
   * 
   * @see com.intellij.codeInsight.AnnotationUtil#isInferredAnnotation(com.intellij.psi.PsiAnnotation)  
   */
  public abstract boolean isInferredAnnotation(@NotNull PsiAnnotation annotation);
}
