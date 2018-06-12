// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.types;

import org.jetbrains.annotations.NotNull;

public interface JvmTypeVisitor<T> {

  T visitType(@NotNull JvmType type);

  T visitReferenceType(@NotNull JvmReferenceType type);

  T visitPrimitiveType(@NotNull JvmPrimitiveType type);

  T visitArrayType(@NotNull JvmArrayType type);

  T visitWildcardType(@NotNull JvmWildcardType type);
}
