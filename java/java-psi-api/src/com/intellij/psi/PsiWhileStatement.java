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
 * Represents a Java {@code while} statement.
 */
public interface PsiWhileStatement extends PsiConditionalLoopStatement {
  /**
   * Returns the opening parenthesis enclosing the statement condition.
   *
   * @return the opening parenthesis, or null if the statement is incomplete.
   */
  @Nullable
  PsiJavaToken getLParenth();

  /**
   * Returns the closing parenthesis enclosing the statement condition.
   *
   * @return the closing parenthesis, or null if the statement is incomplete.
   */
  @Nullable
  PsiJavaToken getRParenth();
}
