package com.intellij.openapi.util.diff.requests;

import com.intellij.openapi.util.diff.chains.DiffRequestPresentable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ErrorDiffRequest extends DiffRequestBase {
  @NotNull private final DiffRequestPresentable myPresentable;
  @NotNull private final String myErrorMessage;
  @Nullable private final Throwable myException;

  public ErrorDiffRequest(@NotNull DiffRequestPresentable presentable, @NotNull Throwable e) {
    this(presentable, e.getMessage(), e);
  }

  public ErrorDiffRequest(@NotNull DiffRequestPresentable presentable, @NotNull String message) {
    this(presentable, message, null);
  }

  public ErrorDiffRequest(@NotNull DiffRequestPresentable presentable,
                          @NotNull String message,
                          @Nullable Throwable e) {
    myPresentable = presentable;
    myErrorMessage = message;
    myException = e;
  }

  @NotNull
  public DiffRequestPresentable getPresentable() {
    return myPresentable;
  }

  @NotNull
  public String getErrorMessage() {
    return myErrorMessage;
  }

  @Nullable
  public Throwable getException() {
    return myException;
  }

  @NotNull
  @Override
  public String getWindowTitle() {
    return myPresentable.getName();
  }
}
