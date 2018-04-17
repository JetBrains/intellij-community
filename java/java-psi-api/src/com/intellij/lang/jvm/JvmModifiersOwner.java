// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import org.jetbrains.annotations.NotNull;

import static com.intellij.util.containers.ContainerUtil.filter;

/**
 * Represents an element which has modifiers and annotations.
 */
public interface JvmModifiersOwner extends JvmAnnotatedElement {

  /**
   * @deprecated To be removed in 2018.3
   */
  @Deprecated
  @NotNull
  default JvmModifier[] getModifiers() {
    return filter(JvmModifier.values(), this::hasModifier).toArray(new JvmModifier[0]);
  }

  /**
   * Checks if the element effectively has the specified modifier.
   *
   * @param modifier the modifier to check
   * @return true if the element has the modifier, false otherwise
   */
  boolean hasModifier(@NotNull JvmModifier modifier);
}
