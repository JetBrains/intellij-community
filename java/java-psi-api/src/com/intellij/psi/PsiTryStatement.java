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

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a Java {@code try ... catch ... finally} statement.
 */
public interface PsiTryStatement extends PsiStatement {
  /**
   * Returns the code block executed by the {@code try} statement.
   *
   * @return the code block instance, or null if the statement is incomplete.
   */
  @Nullable
  PsiCodeBlock getTryBlock();

  /**
   * Returns the array of code blocks executed in the {@code catch} sections
   * of the statement.
   *
   * @return the array of code blocks, or an empty array if the statement has no catch sections.
   */
  PsiCodeBlock @NotNull [] getCatchBlocks();

  /**
   * Returns the array of parameters for {@code catch} sections.
   *
   * @return the array of parameters, or an empty array if the statement has no catch sections.
   */
  PsiParameter @NotNull [] getCatchBlockParameters();

  /**
   * Returns the array of {@code catch} sections in the statement.
   *
   * @return the array of {@code catch} sections, or an empty array if the statement
   * has no catch sections.
   */
  PsiCatchSection @NotNull [] getCatchSections();

  /**
   * Returns the code block executed in the {@code finally} section.
   *
   * @return the code block for the {@code finally} section, or null if the statement
   * does not have one.
   */
  @Nullable
  PsiCodeBlock getFinallyBlock();

  /**
   * Returns a resource list of try-with-resources statement.
   *
   * @return resource list, or null if the statement doesn't have it.
   */
  @Nullable
  PsiResourceList getResourceList();
}
