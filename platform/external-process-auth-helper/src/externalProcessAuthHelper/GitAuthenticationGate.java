// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public interface GitAuthenticationGate {
  <T> T waitAndCompute(@NotNull Supplier<T> operation);
  void cancel();

  @Nullable
  String getSavedInput(@NotNull String key);

  void saveInput(@NotNull String key, @NotNull String value);
}
