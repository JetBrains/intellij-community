// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.diagnostic.IdeaReportingEvent.TextBasedThrowable;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/** @deprecated use {@link LogMessage} */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
@SuppressWarnings({"DeprecatedIsStillUsed", "unused"})
public class LogMessageEx extends LogMessage {

  // needed for compatibility - some reporters expect/check for instances of this class
  LogMessageEx(Throwable throwable, String message, List<Attachment> attachments, String notificationText) {
    super(throwable, message, attachments);
  }

  /** @deprecated use {@link Logger#error(String, Throwable, Attachment...)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static IdeaLoggingEvent createEvent(String message, String details, Attachment... attachments) {
    return createEvent(new TextBasedThrowable(details), message, attachments);
  }

}