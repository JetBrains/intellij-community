// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import org.jetbrains.annotations.NotNull;

/**
 * Represents an element which has modifiers and annotations.
 */
public interface JvmModifiersOwner extends JvmAnnotatedElement {

  /**
   * Checks if the element effectively has the specified modifier.
   *
   * @param modifier the modifier to check
   * @return true if the element has the modifier, false otherwise
   */
  boolean hasModifier(@NotNull JvmModifier modifier);
}
