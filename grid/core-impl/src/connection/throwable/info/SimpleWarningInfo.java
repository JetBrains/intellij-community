package com.intellij.database.connection.throwable.info;

import com.intellij.database.datagrid.GridUtilCore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

final class SimpleWarningInfo extends SimpleThrowableInfo implements WarningInfo {
  private final boolean myIsUserOutput;

  SimpleWarningInfo(@Nls @NotNull String message, @NotNull Throwable throwable) {
    super(message, throwable);
    myIsUserOutput = GridUtilCore.isUserOutput(throwable);
  }

  SimpleWarningInfo(@Nls @NotNull String message, boolean isUserOutput) {
    super(message);
    myIsUserOutput = isUserOutput;
  }

  SimpleWarningInfo(@NotNull Throwable throwable) {
    super(throwable);
    myIsUserOutput = GridUtilCore.isUserOutput(throwable);
  }

  @Override
  public boolean isUserOutput() {
    return myIsUserOutput;
  }
}
