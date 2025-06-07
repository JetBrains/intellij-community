// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
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
  private final @Nullable IdeaPluginDescriptor myPlugin;
  private final @Nullable Object myData;

  public IdeaLoggingEvent(String message, Throwable throwable) {
    this(message, throwable, null);
  }

  public IdeaLoggingEvent(String message, Throwable throwable, @Nullable Object data) {
    myMessage = message;
    myThrowable = throwable;
    myAttachments = List.of();
    myPlugin = null;
    myData = data;
  }

  public IdeaLoggingEvent(
    @Nullable String message,
    @NotNull Throwable throwable,
    @NotNull List<Attachment> attachments,
    @Nullable IdeaPluginDescriptor plugin,
    @Nullable Object data
  ) {
    myMessage = message;
    myThrowable = throwable;
    myAttachments = Collections.unmodifiableList(attachments);
    myPlugin = plugin;
    myData = data;
  }

  /** Returns a message passed to {@link Logger#error Logger.error(String, [...])} methods. */
  public @Nullable String getMessage() {
    return myMessage;
  }

  /**
   * Returns a throwable.
   * If the object comes from {@link com.intellij.diagnostic.IdeErrorsDialog} and a text was edited by a user,
   * the returned throwable only partially resembles an original exception.
   * Prefer {@link #getThrowableText()}.
   */
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

  /** Returns a descriptor of a plugin in which an exception has occurred. */
  public @Nullable IdeaPluginDescriptor getPlugin() {
    return myPlugin;
  }

  public @Nullable Object getData() {
    return myData;
  }

  @Override
  public String toString() {
    return "IdeaLoggingEvent[message=" + myMessage + ", throwable=" + getThrowableText() + "]";
  }
}
