// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.storage.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TransientTree {

  int getKeySize();

  int getBase();

  @Nullable
  byte[] get(@NotNull Novelty.Accessor novelty, @NotNull byte[] key);

  boolean forEach(@NotNull Novelty.Accessor novelty, @NotNull KeyValueConsumer consumer);

  boolean forEach(@NotNull Novelty.Accessor novelty, @NotNull byte[] fromKey, @NotNull KeyValueConsumer consumer);

  TransientTree put(@NotNull Novelty.Accessor novelty, @NotNull byte[] key, @NotNull byte[] value);

  TransientTree put(@NotNull Novelty.Accessor novelty, @NotNull byte[] key, @NotNull byte[] value, boolean overwrite);

  TransientTree delete(@NotNull Novelty.Accessor novelty, @NotNull byte[] key);

  TransientTree delete(@NotNull Novelty.Accessor novelty, @NotNull byte[] key, @Nullable byte[] value);

  TransientTree flush();
}
