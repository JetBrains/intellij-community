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
 * Represents a Java <code>synchronized</code> statement.
 */
public interface PsiSynchronizedStatement extends PsiStatement {
  /**
   * Returns the expression representing the value on which the lock is acquired.
   *
   * @return the expression for the value, or null if the statement is incomplete.
   */
  @Nullable
  PsiExpression getLockExpression();

  /**
   * Returns the body of the statement.
   *
   * @return the body of the statement, or null if the statement is incomplete. 
   */
  @Nullable
  PsiCodeBlock getBody();
}
