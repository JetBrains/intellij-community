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

public interface PsiLiteral extends PsiAnnotationMemberValue {
  /**
   * Returns the value of the literal expression (an Integer for an integer constant, a String
   * for a string literal, and so on).
   *
   * @return the value of the expression, or null if the parsing of the literal failed.
   */
  @Nullable
  Object getValue();
}
