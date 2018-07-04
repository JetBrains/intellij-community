// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.diagnostic.IdeaReportingEvent.TextBasedThrowable;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** @deprecated use {@link LogMessage} (to be removed in IDEA 2020) */
@Deprecated
@SuppressWarnings({"DeprecatedIsStillUsed", "unused"})
public class LogMessageEx extends LogMessage {
  /** @deprecated use {@link Logger#error(String, Throwable, Attachment...)} or {@link LogMessage} (to be removed in IDEA 2020) */
  @Deprecated
  public LogMessageEx(IdeaLoggingEvent event, String title, String notificationText) {
    super(event.getThrowable(), event.getMessage(), attachments(event));
  }

  // needed for compatibility - some reporters expect/check for instances of this class
  LogMessageEx(Throwable throwable, String message, List<Attachment> attachments, String notificationText) {
    super(throwable, message, attachments);
  }

  private static List<Attachment> attachments(IdeaLoggingEvent event) {
    Object data = event.getData();
    return data instanceof AbstractMessage ? ((AbstractMessage)data).getAllAttachments() : Collections.emptyList();
  }

  /** @deprecated use {@link Logger#error(String, Throwable, Attachment...)} (to be removed in IDEA 2020) */
  @Deprecated
  public static IdeaLoggingEvent createEvent(String message, String details, Attachment... attachments) {
    return createEvent(new TextBasedThrowable(details), message, attachments);
  }

  /** @deprecated use {@link Logger#error(String, Throwable, Attachment...)} (to be removed in IDEA 2020) */
  @Deprecated
  public static IdeaLoggingEvent createEvent(String message, String details, String title, String notificationText, Attachment attachment) {
    return createEvent(new TextBasedThrowable(details), message, attachment);
  }

  /** @deprecated use {@link Logger#error(String, Throwable, Attachment...)} (to be removed in IDEA 2020) */
  @Deprecated
  public static IdeaLoggingEvent createEvent(String message, String details, String title, String notificationText, Collection<Attachment> attachments) {
    Attachment[] array = attachments != null ? attachments.toArray(Attachment.EMPTY_ARRAY) : Attachment.EMPTY_ARRAY;
    return createEvent(new TextBasedThrowable(details), message, array);
  }

  /** @deprecated use {@link Logger#error(String, Throwable, Attachment...)} and {@link AttachmentFactory#createContext} (to be removed in IDEA 2020) */
  @Deprecated
  public static void error(@NotNull Logger logger, @NotNull String message, @NotNull String... attachmentText) {
    error(logger, message, new Throwable(), attachmentText);
  }

  /** @deprecated use {@link Logger#error(String, Throwable, Attachment...)} and {@link AttachmentFactory#createContext} (to be removed in IDEA 2020) */
  @Deprecated
  public static void error(Logger logger, String message, Throwable cause, String... attachmentText) {
    StringBuilder detailsBuffer = new StringBuilder();
    for (String detail : attachmentText) {
      detailsBuffer.append(detail).append(",");
    }
    if (attachmentText.length > 0 && detailsBuffer.length() > 0) {
      detailsBuffer.setLength(detailsBuffer.length() - 1);
    }
    if (detailsBuffer.length() > 0) {
      logger.error(message, cause, AttachmentFactory.createContext(detailsBuffer));
    }
    else {
      logger.error(message, cause);
    }
  }
}