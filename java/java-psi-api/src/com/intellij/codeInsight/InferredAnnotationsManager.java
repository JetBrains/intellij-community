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

public abstract class InferredAnnotationsManager {
  private static final NotNullLazyKey<InferredAnnotationsManager, Project> INSTANCE_KEY = ServiceManager.createLazyKey(InferredAnnotationsManager.class);

  public static InferredAnnotationsManager getInstance(@NotNull Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  @Nullable
  public abstract PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);

  @NotNull
  public abstract PsiAnnotation[] findInferredAnnotations(@NotNull PsiModifierListOwner listOwner);

  public abstract boolean isInferredAnnotation(@NotNull PsiAnnotation annotation);
}
