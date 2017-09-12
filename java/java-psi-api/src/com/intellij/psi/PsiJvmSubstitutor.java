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

import com.intellij.lang.jvm.JvmTypeParameter;
import com.intellij.lang.jvm.types.JvmSubstitutor;
import com.intellij.lang.jvm.types.JvmType;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Experimental
public class PsiJvmSubstitutor implements JvmSubstitutor {

  private final @NotNull PsiSubstitutor mySubstitutor;

  public PsiJvmSubstitutor(@NotNull PsiSubstitutor substitutor) {
    mySubstitutor = substitutor;
  }

  @Nullable
  @Override
  public JvmType substitute(@NotNull JvmTypeParameter typeParameter) {
    if (!(typeParameter instanceof PsiTypeParameter)) return null;
    PsiTypeParameter psiTypeParameter = ((PsiTypeParameter)typeParameter);
    return mySubstitutor.substitute(psiTypeParameter);
  }
}
