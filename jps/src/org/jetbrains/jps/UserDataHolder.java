package org.jetbrains.jps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface UserDataHolder {
  @Nullable <T> T getUserData(@NotNull Key<T> key);

  <T> void putUserData(@NotNull Key<T> key, @Nullable T value);
}