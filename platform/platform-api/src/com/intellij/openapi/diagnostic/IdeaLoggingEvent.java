// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

public class IdeaLoggingEvent {
  private final String myMessage;
  private final Throwable myThrowable;
  private final List<Attachment> myAttachments;
  private final @Nullable Object myData;

  public IdeaLoggingEvent(String message, Throwable throwable) {
    this(message, throwable, null);
  }

  public IdeaLoggingEvent(String message, Throwable throwable, @Nullable Object data) {
    this(message, throwable, List.of(), data);
  }

  public IdeaLoggingEvent(
    String message,
    Throwable throwable,
    @NotNull List<Attachment> attachments,
    @Nullable Object data
  ) {
    myMessage = message;
    myThrowable = throwable;
    myAttachments = Collections.unmodifiableList(attachments);
    myData = data;
  }

  public String getMessage() {
    return myMessage;
  }

  public Throwable getThrowable() {
    return myThrowable;
  }

  public @NotNull String getThrowableText() {
    return myThrowable != null ? StringUtil.getThrowableText(myThrowable) : "";
  }

  /** Returns a (possibly empty) list of attachments marked by a user to be included in the error report. */
  public @Unmodifiable @NotNull List<Attachment> getAttachments() {
    return myAttachments;
  }

  public @Nullable Object getData() {
    return myData;
  }

  @Override
  public String toString() {
    return "IdeaLoggingEvent[message=" + myMessage + ", throwable=" + getThrowableText() + "]";
  }
}
