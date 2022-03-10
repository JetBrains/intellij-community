// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

@SuppressWarnings("TypeParameterExtendsFinalClass")
final class TypedBundleImpl<K extends String> implements TypedBundle<K> {

  private final @NotNull DynamicBundle myDynamicBundle;

  TypedBundleImpl(@NotNull String bundleFqn) {
    myDynamicBundle = new DynamicBundle(bundleFqn);
  }

  @Override
  public @NotNull @Nls String message(@NotNull K key, @Nullable Object @NotNull ... params) {
    return myDynamicBundle.getMessage(key, params);
  }

  @Override
  public @NotNull Supplier<@Nls String> lazyMessage(@NotNull K key, @Nullable Object @NotNull ... params) {
    return myDynamicBundle.getLazyMessage(key, params);
  }
}
