package com.intellij.openapi.util.diff.requests;

import com.intellij.openapi.util.diff.chains.DiffRequestPresentable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ErrorDiffRequest extends MessageDiffRequest {
  @Nullable private final DiffRequestPresentable myPresentable;
  @Nullable private final Throwable myException;

  public ErrorDiffRequest(@NotNull String message) {
    this(null, message, null, null);
  }

  public ErrorDiffRequest(@Nullable String title, @NotNull String message) {
    this(title, message, null, null);
  }

  public ErrorDiffRequest(@Nullable String title, @NotNull Throwable e) {
    this(title, e.getMessage(), null, e);
  }

  public ErrorDiffRequest(@NotNull Throwable e) {
    this(null, e.getMessage(), null, e);
  }

  public ErrorDiffRequest(@Nullable DiffRequestPresentable presentable, @NotNull Throwable e) {
    this(presentable != null ? presentable.getName() : null, e.getMessage(), presentable, e);
  }

  public ErrorDiffRequest(@Nullable DiffRequestPresentable presentable, @NotNull String message) {
    this(presentable != null ? presentable.getName() : null, message, presentable, null);
  }

  public ErrorDiffRequest(@Nullable String title,
                          @NotNull String message,
                          @Nullable DiffRequestPresentable presentable,
                          @Nullable Throwable e) {
    super(title, message);
    myPresentable = presentable;
    myException = e;
  }

  @Nullable
  public DiffRequestPresentable getPresentable() {
    return myPresentable;
  }

  @Nullable
  public Throwable getException() {
    return myException;
  }
}
