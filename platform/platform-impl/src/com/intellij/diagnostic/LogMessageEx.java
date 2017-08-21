/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author ksafonov
 */
public class LogMessageEx extends LogMessage {
  private final IdeaLoggingEvent myEvent;
  private final String myTitle;
  private final String myNotificationText;

  /**
   * @param title            text to show in Event Log tool window entry (it comes before 'more')
   * @param notificationText text to show in the error balloon that is popped up automatically
   */
  public LogMessageEx(IdeaLoggingEvent aEvent, String title, String notificationText) {
    super(aEvent);
    myEvent = aEvent;
    myTitle = title;
    myNotificationText = notificationText;
  }

  /**
   * @return text to show in the error balloon that is popped up automatically
   */
  public String getNotificationText() {
    return myNotificationText;
  }

  /**
   * @return text to show in Event Log tool window entry (it comes before 'more')
   */
  public String getTitle() {
    return myTitle;
  }

  public IdeaLoggingEvent toEvent() {
    return myEvent;
  }

  /**
   * @param userMessage      user-friendly message description (short, single line if possible)
   * @param details          technical details (exception stack trace etc.)
   * @param attachments      attachments that will be suggested to include to the report
   */
  public static IdeaLoggingEvent createEvent(String userMessage, final String details, final Attachment... attachments) {
    return createEvent(userMessage, details, userMessage, null, Arrays.asList(attachments));
  }


  /**
   * @param userMessage      user-friendly message description (short, single line if possible)
   * @param details          technical details (exception stack trace etc.)
   * @param title            text to show in Event Log tool window entry (it comes before 'more'), use {@code null} to reuse {@code userMessage}
   * @param notificationText text to show in the error balloon that is popped up automatically. Default is {@code com.intellij.diagnostic.IdeMessagePanel#INTERNAL_ERROR_NOTICE}
   * @param attachments      attachments that will be suggested to include to the report
   */
  public static IdeaLoggingEvent createEvent(final String userMessage,
                                             final String details,
                                             @Nullable final String title,
                                             @Nullable final String notificationText,
                                             final Collection<Attachment> attachments) {
    final Throwable throwable = new Throwable() {
      @Override
      public void printStackTrace(PrintWriter s) {
        s.print(details);
      }

      @Override
      public void printStackTrace(PrintStream s) {
        s.print(details);
      }
    };

    return new IdeaLoggingEvent(userMessage, throwable) {
      @Override
      public Object getData() {
        final LogMessageEx logMessageEx = new LogMessageEx(this, title != null ? title : userMessage, notificationText);
        for (Attachment attachment : attachments) {
          logMessageEx.addAttachment(attachment);
        }
        return logMessageEx;
      }
    };
  }

  public static void error(@NotNull Logger logger, @NonNls @NotNull String message, @NotNull String... attachmentText) {
    error(logger, message, new Throwable(), attachmentText);
  }

  public static void error(@NotNull Logger logger,
                           @NotNull String message,
                           @NotNull Throwable cause,
                           @NotNull String... attachmentText) {
    StringBuilder detailsBuffer = new StringBuilder();
    for (String detail : attachmentText) {
      detailsBuffer.append(detail).append(",");
    }
    if (attachmentText.length > 0 && detailsBuffer.length() > 0) {
      detailsBuffer.setLength(detailsBuffer.length() - 1);
    }
    Attachment attachment = detailsBuffer.length() > 0 ? new Attachment("current-context.txt", detailsBuffer.toString()) : null;
    logger.error(createEvent(message, ExceptionUtil.getThrowableText(cause), null, null, attachment));
  }

  /**
   * @param userMessage      user-friendly message description (short, single line if possible)
   * @param details          technical details (exception stack trace etc.)
   * @param title            text to show in Event Log tool window entry (it comes before 'more'), use {@code null} to reuse {@code userMessage}
   * @param notificationText text to show in the error balloon that is popped up automatically. Default is {@code com.intellij.diagnostic.IdeMessagePanel#INTERNAL_ERROR_NOTICE}
   * @param attachment       attachment that will be suggested to include to the report
   */
  public static IdeaLoggingEvent createEvent(String userMessage,
                                             final String details,
                                             @Nullable final String title,
                                             @Nullable final String notificationText,
                                             @Nullable Attachment attachment) {
    return createEvent(userMessage, details, title, notificationText,
                       attachment != null ? Collections.singletonList(attachment) : Collections.emptyList());
  }
}
