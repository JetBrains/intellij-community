// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import com.intellij.lang.jvm.types.JvmType;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a field.
 *
 * @see java.lang.reflect.Field
 */
public interface JvmField extends JvmMember {

  /**
   * @see java.lang.reflect.Field#getName
   */
  @NotNull
  @Override
  String getName();

  /**
   * @see java.lang.reflect.Field#getGenericType
   * @see java.lang.reflect.Field#getAnnotatedType
   */
  @NotNull
  JvmType getType();

  @Override
  default <T> T accept(@NotNull JvmElementVisitor<T> visitor) {
    return visitor.visitField(this);
  }
}
