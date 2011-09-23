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
 * Represents a Java code fragment the contents of which is a reference to a Java type.
 *
 * @author dsl
 * @see PsiElementFactory#createTypeCodeFragment(String, PsiElement, boolean)
 */
public interface PsiTypeCodeFragment extends JavaCodeFragment {
  /**
   * Returns the type referenced by the code fragment.
   *
   * @return the referenced type.
   * @throws TypeSyntaxException if the code fragment contains a syntax error.
   * @throws NoTypeException if the contents of the code fragment is not a Java type.
   */
  @NotNull
  PsiType getType()
    throws TypeSyntaxException, NoTypeException;

  /**
   * Checks if <code>void</code> is treated as a valid type for the contents of
   * the code fragment.
   *
   * @return true if <code>void</code> is a valid type, false otherwise.
   */
  boolean isVoidValid();

  class IncorrectTypeException extends Exception {
    public IncorrectTypeException(final String message) { super(message); }
  }

  class TypeSyntaxException extends IncorrectTypeException {
    public TypeSyntaxException(final String message) { super(message); }
  }

  class NoTypeException extends IncorrectTypeException {
    public NoTypeException(final String message) { super(message); }
  }
}
