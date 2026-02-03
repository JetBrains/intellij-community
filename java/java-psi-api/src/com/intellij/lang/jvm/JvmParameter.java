// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import com.intellij.lang.jvm.types.JvmType;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a parameter of a method or a constructor.
 *
 * @see java.lang.reflect.Parameter
 */
public interface JvmParameter extends JvmNamedElement, JvmModifiersOwner {

  /**
   * @see java.lang.reflect.Parameter#getParameterizedType
   * @see java.lang.reflect.Parameter#getAnnotatedType
   */
  @NotNull
  JvmType getType();

  @Override
  default <T> T accept(@NotNull JvmElementVisitor<T> visitor) {
    return visitor.visitParameter(this);
  }
}
