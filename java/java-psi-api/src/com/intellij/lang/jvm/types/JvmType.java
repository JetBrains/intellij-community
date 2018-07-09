// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.types;

import com.intellij.lang.jvm.JvmAnnotation;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an type which is supported by JVM.
 *
 * @see java.lang.reflect.Type
 * @see java.lang.reflect.AnnotatedType
 */
public interface JvmType {

  /**
   * @return type annotations
   * @see java.lang.reflect.AnnotatedType#getAnnotations
   */
  @NotNull
  JvmAnnotation[] getAnnotations();

  default <T> T accept(@NotNull JvmTypeVisitor<T> visitor) {
    return visitor.visitType(this);
  }
}
