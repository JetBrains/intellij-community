package com.intellij.database.connection.throwable.info;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SimpleErrorInfo extends SimpleThrowableInfo implements ErrorInfo {
  private final List<Fix> myFixes;

  @Contract("null, null, _ -> fail")
  public SimpleErrorInfo(@Nls @Nullable String message, @Nullable Throwable throwable, @NotNull List<Fix> fixes) {
    super(message == null ? ThrowableInfoUtil.getDefaultMessage(Objects.requireNonNull(throwable)) : message, throwable);
    myFixes = fixes;
  }

  @Override
  public @NotNull List<Fix> getFixes() {
    return myFixes;
  }

  public static ErrorInfo create(@Nls @NotNull String message, @Nullable Throwable throwable, @NotNull List<Fix> fixes) {
    return new SimpleErrorInfo(message, throwable, fixes);
  }

  public static ErrorInfo create(@Nls @NotNull String message, @Nullable Throwable throwable) {
    return create(message, throwable, Collections.emptyList());
  }

  public static ErrorInfo create(@Nls @NotNull String message) {
    return create(message, null);
  }

  public static ErrorInfo create(@NotNull Throwable throwable) {
    return new SimpleErrorInfo(null, throwable, Collections.emptyList());
  }
}
