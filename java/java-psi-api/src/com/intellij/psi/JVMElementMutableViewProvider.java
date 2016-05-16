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
package com.intellij.psi;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.util.Pass;
import org.jetbrains.annotations.NotNull;

public interface JVMElementMutableViewProvider {
  LanguageExtension<JVMElementMutableViewProvider> EXTENSION_POINT =
    new LanguageExtension<JVMElementMutableViewProvider>("com.intellij.psi.jvmElementMutableViewProvider");

  /**
   * Create mutable view corresponding to the rootElement, let updater modify it, then propagate changes back to the rootElement
   */
  void runWithMutableView(@NotNull PsiElement rootElement,
                          @NotNull Pass<JVMElementMutableView> updater);
}