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

/**
 * Represents a single <code>case</code> or <code>default</code> section in a
 * Java <code>switch</code> statement.
 */
public interface PsiSwitchLabelStatement extends PsiStatement {
  /**
   * Checks if the element represents a <code>default</code> section.
   *
   * @return true if the element represents a <code>default</code> section, false otherwise.
   */
  boolean isDefaultCase();

  /**
   * Returns the constant associated with the <code>case</code> block.
   *
   * @return the associated constant, or null if the statement is incomplete or the element
   * represents a <code>default</code> section.
   */
  @Nullable
  PsiExpression getCaseValue();

  /**
   * Returns the <code>switch</code> statement with which the section is associated.
   *
   * @return the associated statement, or null if the element is not valid in its current context.
   */
  @Nullable
  PsiSwitchStatement getEnclosingSwitchStatement();
}
