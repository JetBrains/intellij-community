package com.intellij.openapi.util.diff.requests;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ErrorDiffRequest extends DiffRequestBase {
  @NotNull private final DiffRequestPresentable myPresentable;
  @NotNull private final String myErrorMessage;
  @Nullable private final Throwable myException;
  @Nullable private final List<? extends AnAction> myAdditionalActions;

  public ErrorDiffRequest(@NotNull DiffRequestPresentable presentable, @NotNull Throwable e) {
    this(presentable, e.getMessage(), e, null);
  }

  public ErrorDiffRequest(@NotNull DiffRequestPresentable presentable, @NotNull String message) {
    this(presentable, message, null, null);
  }

  public ErrorDiffRequest(@NotNull DiffRequestPresentable presentable,
                          @NotNull String message,
                          @Nullable List<? extends AnAction> actions) {
    this(presentable, message, null, actions);
  }

  public ErrorDiffRequest(@NotNull DiffRequestPresentable presentable,
                          @NotNull String message,
                          @Nullable Throwable e,
                          @Nullable List<? extends AnAction> actions) {
    myPresentable = presentable;
    myErrorMessage = message;
    myException = e;
    myAdditionalActions = actions;
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

  @Nullable
  @Override
  public List<? extends AnAction> getActions() {
    return myAdditionalActions;
  }
}
