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
package com.intellij.psi.impl.jvm2psi;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JvmPsiConversionHelper {

  private final @NotNull PsiManager myManager;

  public JvmPsiConversionHelper(@NotNull PsiManager manager) {
    myManager = manager;
  }

  @NotNull
  public PsiManager getManager() {
    return myManager;
  }

  @Contract("null -> null; !null -> !null")
  @Nullable
  public PsiClass toPsiClass(@Nullable JvmClass jvmClass) {
    // TODO
    throw new RuntimeException("TODO");
  }
}
