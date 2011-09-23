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
 * Represents a Java <code>import</code> statement.
 */
public interface PsiImportStatement extends PsiImportStatementBase {
  /**
   * The empty array of PSI import statements which can be reused to avoid unnecessary allocations.
   */
  PsiImportStatement[] EMPTY_ARRAY = new PsiImportStatement[0];

  /**
   * Returns the full-qualified name of the imported class or package.
   *
   * @return the full-qualified name, or null if the statement is incomplete.
   */
  @Nullable
  String getQualifiedName();
}
