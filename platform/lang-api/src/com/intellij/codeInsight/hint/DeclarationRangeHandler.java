/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.hint;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.MixinEP;
import org.jetbrains.annotations.NotNull;

/**
 * Returns the subset of the text range of the specified element which is considered its declaration.
 * For example, the declaration range of a method includes its modifiers, return type, name and
 * parameter list.
 */
public interface DeclarationRangeHandler<T extends PsiElement>{
  ExtensionPointName<MixinEP<DeclarationRangeHandler>> EP_NAME = ExtensionPointName.create("com.intellij.declarationRangeHandler");

  /**
   * Returns the declaration range for the specified container.
   * @param container the container
   * @return the declaration range for it.
   */
  @NotNull
  TextRange getDeclarationRange(@NotNull T container);
}
