/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class LogEventException extends RuntimeException implements ExceptionWithAttachments {
  private final IdeaLoggingEvent myLogMessage;

  public LogEventException(String userMessage, final String details, final Attachment... attachments) {
    this(LogMessageEx.createEvent(userMessage, details + "\n\n" + DebugUtil.currentStackTrace(), attachments));
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
      return throwable == null ? attachments : ArrayUtil.prepend(new Attachment("details.trace", throwable), attachments);
    }
    return Attachment.EMPTY_ARRAY;
  }
}
