// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Internal API. See a note in {@link MessagePool}. */
@ApiStatus.Internal
public final class LogMessage extends AbstractMessage {
  private final Throwable myThrowable;
  private final String myMessage;
  private final List<Attachment> myAttachments;

  public LogMessage(@NotNull Throwable throwable, @Nullable String message, @NotNull List<Attachment> attachments) {
    myThrowable = ThrowableInterner.intern(throwable);

    var str = message;
    if (str != null && throwable.getMessage() != null) {
      str = Strings.trimStart(str, throwable.getMessage());
      if (!Strings.areSameInstance(str, message)) {
        str = Strings.trimStart(str, ": ");
      }
    }
    if ("null".equals(str)) {
      str = null;
    }
    myMessage = Strings.nullize(str, true);

    myAttachments = new ArrayList<>(ContainerUtil.filter(attachments, attachment -> attachment != null));
  }

  @Override
  public @NotNull Throwable getThrowable() {
    return myThrowable;
  }

  @Override
  public @NotNull String getThrowableText() {
    return IdeaLogRecordFormatter.formatThrowable(myThrowable);
  }

  @Override
  public @NotNull String getMessage() {
    return myMessage != null ? myMessage : "";
  }

  @Override
  public @NotNull List<Attachment> getAllAttachments() {
    return Collections.unmodifiableList(myAttachments);
  }

  /** @deprecated use {@link IdeaLoggingEvent#IdeaLoggingEvent} directly */
  @Deprecated(forRemoval = true)
  public static IdeaLoggingEvent eventOf(
    @NotNull Throwable throwable,
    @Nullable String userMessage,
    @NotNull List<@NotNull Attachment> attachments
  ) {
    return new IdeaLoggingEvent(userMessage, throwable, new LogMessage(throwable, userMessage, attachments));
  }

  /** @deprecated use {@link IdeaLoggingEvent#IdeaLoggingEvent} directly */
  @Deprecated(forRemoval = true)
  public static IdeaLoggingEvent createEvent(@NotNull Throwable throwable, @Nullable String userMessage, Attachment @NotNull ... attachments) {
    return new IdeaLoggingEvent(userMessage, throwable, new LogMessage(throwable, userMessage, List.of(attachments)));
  }
}
