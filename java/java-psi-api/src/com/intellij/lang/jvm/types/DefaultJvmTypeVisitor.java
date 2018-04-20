// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.types;

import org.jetbrains.annotations.NotNull;

public interface DefaultJvmTypeVisitor<T> extends JvmTypeVisitor<T> {

  default T visitReferenceType(@NotNull JvmReferenceType type) {
    return visitType(type);
  }

  default T visitPrimitiveType(@NotNull JvmPrimitiveType type) {
    return visitType(type);
  }

  default T visitArrayType(@NotNull JvmArrayType type) {
    return visitType(type);
  }

  default T visitWildcardType(@NotNull JvmWildcardType type) {
    return visitType(type);
  }
}
