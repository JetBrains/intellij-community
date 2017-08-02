/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.lang.jvm;

import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ArrayUtil.contains;

/**
 * Represents an element which has modifiers and annotations.
 */
public interface JvmModifiersOwner extends JvmAnnotatedElement {

  @NotNull
  JvmModifier[] getModifiers();

  /**
   * Checks if the element effectively has the specified modifier.
   *
   * @param modifier the modifier to check
   * @return true if the element has the modifier, false otherwise
   */
  default boolean hasModifier(@NotNull JvmModifier modifier) {
    return contains(getModifiers(), modifier);
  }
}
