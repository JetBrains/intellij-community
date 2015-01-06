package com.intellij.openapi.util.diff.requests;

import com.intellij.openapi.util.diff.chains.DiffRequestPresentable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ErrorDiffRequest extends DiffRequestBase {
  @Nullable private final DiffRequestPresentable myPresentable;
  @NotNull private final String myErrorMessage;
  @Nullable private final Throwable myException;

  public ErrorDiffRequest(@NotNull String message) {
    this(null, message, null);
  }

  public ErrorDiffRequest(@NotNull Throwable e) {
    this(null, e.getMessage(), e);
  }

  public ErrorDiffRequest(@Nullable DiffRequestPresentable presentable, @NotNull Throwable e) {
    this(presentable, e.getMessage(), e);
  }

  public ErrorDiffRequest(@Nullable DiffRequestPresentable presentable, @NotNull String message) {
    this(presentable, message, null);
  }

  public ErrorDiffRequest(@Nullable DiffRequestPresentable presentable,
                          @NotNull String message,
                          @Nullable Throwable e) {
    myPresentable = presentable;
    myErrorMessage = message;
    myException = e;
  }

  @Nullable
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

  @Nullable
  @Override
  public String getTitle() {
    return myPresentable != null ? myPresentable.getName() : null;
  }
}
