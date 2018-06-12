// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.types;

import org.jetbrains.annotations.NotNull;

/**
 * @see Class#isArray
 */
public interface JvmArrayType extends JvmType {

  /**
   * @return component type of an array. That is:
   * <ul>
   * <li> for {@code int[]}: {@code int}
   * <li> for {@code String[][]}: {@code String[]}
   * </ul>
   * @see Class#getComponentType
   */
  @NotNull
  JvmType getComponentType();

  @Override
  default <T> T accept(@NotNull JvmTypeVisitor<T> visitor) {
    return visitor.visitArrayType(this);
  }
}
