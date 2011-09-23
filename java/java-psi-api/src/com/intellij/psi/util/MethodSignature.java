/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public interface MethodSignature {
  MethodSignature[] EMPTY_ARRAY = new MethodSignature[0];

  @NotNull
  PsiSubstitutor getSubstitutor();

  @NotNull
  String getName();

  /**
   * @return array of parameter types (already substituted)
   */
  @NotNull
  PsiType[] getParameterTypes();

  @NotNull
  PsiTypeParameter[] getTypeParameters();

  boolean isRaw();

  boolean isConstructor();
}
