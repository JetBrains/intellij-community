// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LogMessage extends AbstractMessage {
  private final Throwable myThrowable;
  private final String myMessage;
  private final List<Attachment> myAttachments;

  LogMessage(Throwable throwable, String message, List<Attachment> attachments) {
    myThrowable = ThrowableInterner.intern(throwable);

    String str = message;
    if (str != null && throwable.getMessage() != null) {
      str = StringUtil.trimStart(str, throwable.getMessage());
      if (str != message) {
        str = StringUtil.trimStart(str, ": ");
      }
    }
    if ("null".equals(str)) {
      str = null;
    }
    myMessage = StringUtil.nullize(str, true);

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

  /** @deprecated pass all attachments to {@link #createEvent(Throwable, String, Attachment...)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public synchronized void addAttachment(@NotNull Attachment attachment) {
    myAttachments.add(attachment);
  }

  // factory methods

  /**
   * @param userMessage      user-friendly message description (short, single line if possible)
   * @param attachments      attachments that will be suggested to include to the report
   */
  public static IdeaLoggingEvent createEvent(@NotNull Throwable throwable, @Nullable String userMessage, Attachment @NotNull ... attachments) {
    AbstractMessage message = new LogMessage(throwable, userMessage, Arrays.asList(attachments));
    return new IdeaLoggingEvent(userMessage, throwable, message);
  }
}