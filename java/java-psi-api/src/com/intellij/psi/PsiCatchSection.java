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
package com.intellij.psi;

import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a single <code>catch</code> section of a Java <code>try ... catch</code> statement.
 *
 * @author ven
 */
public interface PsiCatchSection extends PsiElement {
  /**
   * The empty array of PSI catch sections which can be reused to avoid unnecessary allocations.
   */
  PsiCatchSection[] EMPTY_ARRAY = new PsiCatchSection[0];

  ArrayFactory<PsiCatchSection> ARRAY_FACTORY = new ArrayFactory<PsiCatchSection>() {
    @NotNull
    @Override
    public PsiCatchSection[] create(final int count) {
      return count == 0 ? EMPTY_ARRAY : new PsiCatchSection[count];
    }
  };

  /**
   * Returns the variable in which the caught exception is captured.
   *
   * @return the parameter for the called variable, or null if none is specified.
   */
  @Nullable
  PsiParameter getParameter();

  /**
   * Returns the code block contained in the catch section.
   *
   * @return the code block, or null if the section is incomplete.
   */
  @Nullable
  PsiCodeBlock getCatchBlock();

  /**
   * Returns the type of the caught exception.
   *
   * @return the type, or null if the section is incomplete.
   */
  @Nullable
  PsiType getCatchType();

  /**
   * For language level 7 or higher returns the list of possible types for its parameter re-throw
   * (as defined in Project Coin/JSR 334 section "Multi-catch and more precise rethrow").
   * Note that the list may be empty if exceptions of declared catch type are not explicitly thrown
   * inside a try block.
   *
   * Otherwise, returns parameter's declared type.
   *
   * @return the types, or empty list if the section is incomplete.
   */
  @NotNull
  List<PsiType> getPreciseCatchTypes();

  /**
   * Returns the <code>try</code> statement to which the catch section is attached.
   *
   * @return the statement instance.
   */
  @NotNull
  PsiTryStatement getTryStatement();

  @Nullable
  PsiJavaToken getRParenth();

  @Nullable
  PsiJavaToken getLParenth();
}
