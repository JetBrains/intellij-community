// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

/** @deprecated use {@link RuntimeExceptionWithAttachments#RuntimeExceptionWithAttachments(String, String, Attachment...)} (to be removed in IDEA 2020) */
@SuppressWarnings({"DeprecatedIsStillUsed", "unused"})
public class LogEventException extends RuntimeException implements ExceptionWithAttachments {
  private final IdeaLoggingEvent myLogMessage;

  public LogEventException(String userMessage, String details, Attachment... attachments) {
    this(LogMessage.createEvent(new Throwable(details), userMessage, attachments));
  }
  
  public LogEventException(IdeaLoggingEvent logMessage) {
    super(logMessage.getMessage());
    myLogMessage = logMessage;
  }

  public IdeaLoggingEvent getLogMessage() {
    return myLogMessage;
  }

  @NotNull
  @Override
  public Attachment[] getAttachments() {
    Object data = myLogMessage.getData();
    if (data instanceof LogMessageEx) {
      Attachment[] attachments = ((LogMessageEx)data).getAllAttachments().toArray(Attachment.EMPTY_ARRAY);
      Throwable throwable = myLogMessage.getThrowable();
      return throwable == null ? attachments : ArrayUtil.prepend(new Attachment("details", throwable), attachments);
    }
    return Attachment.EMPTY_ARRAY;
  }
}