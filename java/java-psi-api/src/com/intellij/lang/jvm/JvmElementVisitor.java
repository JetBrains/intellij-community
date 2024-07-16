// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import org.jetbrains.annotations.NotNull;

/**
 * @param <T> result of computation
 * @see DefaultJvmElementVisitor
 */
public interface JvmElementVisitor<T> {

  T visitClass(@NotNull JvmClass clazz);

  T visitElement(@NotNull JvmElement element);

  T visitField(@NotNull JvmField field);

  T visitMember(@NotNull JvmMember member);

  T visitMethod(@NotNull JvmMethod method);

  T visitParameter(@NotNull JvmParameter parameter);

  T visitTypeParameter(@NotNull JvmTypeParameter typeParameter);
}
