// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import org.jetbrains.annotations.NotNull;

public interface DefaultJvmElementVisitor<T> extends JvmElementVisitor<T> {

  @Override
  default T visitElement(@NotNull JvmElement element) {
    return null;
  }

  @Override
  default T visitMember(@NotNull JvmMember member) {
    return visitElement(member);
  }

  @Override
  default T visitField(@NotNull JvmField field) {
    return visitMember(field);
  }

  @Override
  default T visitMethod(@NotNull JvmMethod method) {
    return visitMember(method);
  }

  @Override
  default T visitParameter(@NotNull JvmParameter parameter) {
    return visitElement(parameter);
  }

  @Override
  default T visitClass(@NotNull JvmClass clazz) {
    return visitMember(clazz);
  }

  @Override
  default T visitTypeParameter(@NotNull JvmTypeParameter typeParameter) {
    return visitElement(typeParameter);
  }
}
