/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NotNull;

/**
* @author Max Medvedev
*/
public interface ConstructorBodyGenerator {
  LanguageExtension<ConstructorBodyGenerator> INSTANCE = new LanguageExtension<>("com.intellij.constructorBodyGenerator");

  @Deprecated
  void generateFieldInitialization(@NotNull StringBuilder buffer, @NotNull PsiField[] fields, @NotNull PsiParameter[] parameters);

  void generateSuperCallIfNeeded(@NotNull StringBuilder buffer, @NotNull PsiParameter[] parameters);

  StringBuilder start(StringBuilder buffer, @NotNull String name, @NotNull PsiParameter[] parameters);

  void finish(StringBuilder builder);
}
