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
package com.intellij.formatting;

import org.jetbrains.annotations.NotNull;

/**
 * Internal interface for creating alignment object instances.
 */

interface AlignmentFactory {

  /**
   * Provides implementation for {@link Alignment#createAlignment(boolean, Alignment.Anchor)}.
   * 
   * @param allowBackwardShift    flag that specifies if former aligned block may be shifted to right in order to align to subsequent
   *                              aligned block
   * @param anchor                alignment anchor
   * @return                      alignment object with the given settings
   */
  Alignment createAlignment(boolean allowBackwardShift, @NotNull Alignment.Anchor anchor);

  /**
   * Provides 
   *
   * @param base    base alignment to use within returned alignment object
   * @return        alignment object with the given alignment defined as a {@code 'base alignment'}
   */
  Alignment createChildAlignment(final Alignment base);
}
