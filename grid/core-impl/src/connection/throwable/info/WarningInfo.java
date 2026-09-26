package com.intellij.database.connection.throwable.info;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public interface WarningInfo extends ThrowableInfo {
  boolean isUserOutput();


  static WarningInfo create(@Nls @NotNull String message, boolean isUserOutput) {
    return new SimpleWarningInfo(message, isUserOutput);
  }

  static WarningInfo create(@Nls @NotNull String message, @NotNull Throwable throwable) {
    return new SimpleWarningInfo(message, throwable);
  }

  static WarningInfo create(@NotNull Throwable throwable) {
    return new SimpleWarningInfo(throwable);
  }
}
