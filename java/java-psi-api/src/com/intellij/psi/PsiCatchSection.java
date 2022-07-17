// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a single {@code catch} section of a Java {@code try ... catch} statement.
 *
 * @author ven
 */
public interface PsiCatchSection extends PsiElement {
  /**
   * The empty array of PSI catch sections which can be reused to avoid unnecessary allocations.
   */
  PsiCatchSection[] EMPTY_ARRAY = new PsiCatchSection[0];

  ArrayFactory<PsiCatchSection> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiCatchSection[count];

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
   * For language level 7 or higher returns the list of possible types for a rethrow of its parameter
   * (as defined in JLS 11.2.2. Exception Analysis of Statements and Project Coin/JSR 334 section "Multi-catch and more precise rethrow").
   * Note that the list may be empty if the declared exception(s) of the catch section are unchecked or the code is not compilable.
   *
   * Otherwise, returns parameter's declared type.
   *
   * @return a list of types that can be rethrown, or and empty list if the section is incomplete.
   */
  @NotNull
  List<PsiType> getPreciseCatchTypes();

  /**
   * Returns the {@code try} statement to which the catch section is attached.
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
