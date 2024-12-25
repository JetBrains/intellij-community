// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.requests;

import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ErrorDiffRequest extends MessageDiffRequest {
  private final @Nullable DiffRequestProducer myProducer;
  private final @Nullable Throwable myException;

  public ErrorDiffRequest(@NotNull @Nls String message) {
    this(null, message, null, null);
  }

  public ErrorDiffRequest(@Nullable @NlsContexts.DialogTitle String title, @NotNull @Nls String message) {
    this(title, message, null, null);
  }

  public ErrorDiffRequest(@Nullable @NlsContexts.DialogTitle String title, @NotNull Throwable e) {
    this(title, getErrorMessage(e), null, e);
  }

  public ErrorDiffRequest(@NotNull Throwable e) {
    this(null, getErrorMessage(e), null, e);
  }

  public ErrorDiffRequest(@Nullable DiffRequestProducer producer, @NotNull Throwable e) {
    this(producer != null ? producer.getName() : null, getErrorMessage(e), producer, e);
  }

  public ErrorDiffRequest(@Nullable DiffRequestProducer producer, @NotNull @Nls String message) {
    this(producer != null ? producer.getName() : null, message, producer, null);
  }

  public ErrorDiffRequest(@Nullable @NlsContexts.DialogTitle String title,
                          @NotNull @Nls String message,
                          @Nullable DiffRequestProducer producer,
                          @Nullable Throwable e) {
    super(title, message);
    myProducer = producer;
    myException = e;
  }

  public @Nullable DiffRequestProducer getProducer() {
    return myProducer;
  }

  public @Nullable Throwable getException() {
    return myException;
  }

  private static @Nls @NotNull String getErrorMessage(@NotNull Throwable e) {
    String message = e.getMessage();
    return StringUtil.isEmptyOrSpaces(message) ? DiffBundle.message("error.cant.show.diff.message") : message;
  }
}
