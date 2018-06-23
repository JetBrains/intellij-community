// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.types;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a primitive type of JVM.
 *
 * @see Class#isPrimitive
 */
public interface JvmPrimitiveType extends JvmType {

  @NotNull
  JvmPrimitiveTypeKind getKind();

  @Override
  default <T> T accept(@NotNull JvmTypeVisitor<T> visitor) {
    return visitor.visitPrimitiveType(this);
  }
}
