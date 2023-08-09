// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ProtoMember extends Proto {
  @NotNull
  private final TypeRepr type;
  @Nullable
  private final Object value;

  public ProtoMember(JVMFlags flags, String signature, String name, @NotNull TypeRepr type, @NotNull Iterable<TypeRepr.ClassType> annotations, @Nullable Object value) {
    super(flags, signature, name, annotations);
    this.type = type;
    this.value = value;
  }

  public @NotNull TypeRepr getType() {
    return type;
  }

  public @Nullable Object getValue() {
    return value;
  }
}
