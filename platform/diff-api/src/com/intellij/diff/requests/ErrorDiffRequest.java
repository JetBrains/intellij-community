// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.requests;

import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ErrorDiffRequest extends MessageDiffRequest {
  @Nullable private final DiffRequestProducer myProducer;
  @Nullable private final Throwable myException;

  public ErrorDiffRequest(@NotNull String message) {
    this(null, message, null, null);
  }

  public ErrorDiffRequest(@Nullable String title, @NotNull String message) {
    this(title, message, null, null);
  }

  public ErrorDiffRequest(@Nullable String title, @NotNull Throwable e) {
    this(title, getErrorMessage(e), null, e);
  }

  public ErrorDiffRequest(@NotNull Throwable e) {
    this(null, getErrorMessage(e), null, e);
  }

  public ErrorDiffRequest(@Nullable DiffRequestProducer producer, @NotNull Throwable e) {
    this(producer != null ? producer.getName() : null, getErrorMessage(e), producer, e);
  }

  public ErrorDiffRequest(@Nullable DiffRequestProducer producer, @NotNull String message) {
    this(producer != null ? producer.getName() : null, message, producer, null);
  }

  public ErrorDiffRequest(@Nullable String title,
                          @NotNull String message,
                          @Nullable DiffRequestProducer producer,
                          @Nullable Throwable e) {
    super(title, message);
    myProducer = producer;
    myException = e;
  }

  @Nullable
  public DiffRequestProducer getProducer() {
    return myProducer;
  }

  @Nullable
  public Throwable getException() {
    return myException;
  }

  @NotNull
  private static String getErrorMessage(@NotNull Throwable e) {
    String message = e.getMessage();
    return StringUtil.isEmptyOrSpaces(message) ? DiffBundle.message("error.cant.show.diff.message") : message;
  }
}
