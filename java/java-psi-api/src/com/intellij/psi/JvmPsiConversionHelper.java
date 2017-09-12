/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.lang.jvm.JvmTypeDeclaration;
import com.intellij.lang.jvm.JvmTypeParameter;
import com.intellij.lang.jvm.types.JvmSubstitutor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  PsiSubstitutor convertSubstitutor(@NotNull JvmSubstitutor substitutor);
}
