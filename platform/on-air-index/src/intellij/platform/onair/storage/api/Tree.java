// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Tree {
  int getKeySize();

  int getBase();

  @Nullable
  byte[] get(@NotNull Novelty novelty, @NotNull byte[] key);

  boolean put(@NotNull Novelty novelty, @NotNull byte[] key, @NotNull byte[] value);

  boolean put(@NotNull Novelty novelty, @NotNull byte[] key, @NotNull byte[] value, boolean overwrite);

  boolean delete(@NotNull Novelty novelty, @NotNull byte[] key);

  boolean delete(@NotNull Novelty novelty, @NotNull byte[] key, @Nullable byte[] value);

  boolean forEach(@NotNull Novelty novelty, @NotNull KeyValueConsumer consumer);

  Address store(@NotNull Novelty novelty);
}
