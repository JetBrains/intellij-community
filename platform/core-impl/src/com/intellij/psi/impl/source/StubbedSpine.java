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
package com.intellij.psi.impl.source;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the list of stubbed PSI elements in the file, backed by either AST or stubs.
 */
public interface StubbedSpine {
  
  /** @return the number of stubbed elements */
  int getStubCount();

  /** @return the stubbed PSI element at the given index or null if index is greater than the spine size */
  @Nullable
  PsiElement getStubPsi(int index);

  /** 
   * @return the stubbed PSI element's type at the given index or null if index is greater than the spine size.
   * This method avoids PSI element allocation if possible. 
   */
  @Nullable
  IElementType getStubType(int index);
}
