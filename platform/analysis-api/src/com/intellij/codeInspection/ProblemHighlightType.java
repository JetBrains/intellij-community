/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection;

public enum ProblemHighlightType {

  /** Underlying highlighting with color depending on the inspection {@link com.intellij.codeHighlighting.HighlightDisplayLevel} */
  GENERIC_ERROR_OR_WARNING,

  /** Changes font color depending on the inspection {@link com.intellij.codeHighlighting.HighlightDisplayLevel} */
  LIKE_UNKNOWN_SYMBOL,

  LIKE_DEPRECATED,

  LIKE_UNUSED_SYMBOL,

  /** The same as {@link #LIKE_UNKNOWN_SYMBOL} with enforced {@link com.intellij.codeHighlighting.HighlightDisplayLevel#ERROR} severity level */
  ERROR,

  /** The same as {@link #GENERIC_ERROR_OR_WARNING} with enforced {@link com.intellij.codeHighlighting.HighlightDisplayLevel#ERROR} severity level */
  GENERIC_ERROR,

  /** Enforces {@link com.intellij.codeHighlighting.HighlightDisplayLevel#INFO} severity level
   * use #WEAK_WARNING instead*/
  @Deprecated
  INFO,

  /** Enforces {@link com.intellij.codeHighlighting.HighlightDisplayLevel#WEAK_WARNING} severity level */
  WEAK_WARNING,

  /** Enforces {@link com.intellij.codeHighlighting.HighlightDisplayLevel#DO_NOT_SHOW} severity level */
  INFORMATION,

  /** JEP 277 enhanced deprecation */
  LIKE_MARKED_FOR_REMOVAL
}
