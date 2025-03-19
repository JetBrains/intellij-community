// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.animation;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Can be used as a context for {@link Animation} to hold a value.
 */
public final class AnimationContext<T> implements Consumer<T> {

  private @Nullable T value;

  @Override
  public void accept(T t) {
    this.value = t;
  }

  public @Nullable T getValue() {
    return value;
  }
}
