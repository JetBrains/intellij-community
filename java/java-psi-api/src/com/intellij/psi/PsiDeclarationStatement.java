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
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a Java local variable or class declaration statement.
 */
public interface PsiDeclarationStatement extends PsiStatement{
  /**
   * Returns the elements declared by the statement.
   *
   * @return the array of elements, which can contain elements of types {@link PsiLocalVariable} or
   * {@link PsiClass} (and possibly other types).
   */
  @NotNull
  PsiElement[] getDeclaredElements();
}
