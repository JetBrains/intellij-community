// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.idea.IdeaApplication;
import com.intellij.idea.Main;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ErrorLogger;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.util.ExceptionUtil;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Mike
 */
public class DialogAppender extends AppenderSkeleton {
  private static final ErrorLogger[] LOGGERS = {new DefaultIdeaErrorLogger()};
  private static final int MAX_ASYNC_LOGGING_EVENTS = 5;

  private final AtomicInteger myPendingAppendCounts = new AtomicInteger();
  private volatile Runnable myDialogRunnable;

  @Override
  protected synchronized void append(@NotNull LoggingEvent event) {
    if (!event.getLevel().isGreaterOrEqual(Level.ERROR) ||
        Main.isCommandLine() ||
        !IdeaApplication.isLoaded()) {
      return;
    }

    if (myPendingAppendCounts.addAndGet(1) > MAX_ASYNC_LOGGING_EVENTS) {
      // Stop adding requests to the queue or we can get OOME on pending logging requests (IDEA-95327)
      myPendingAppendCounts.decrementAndGet(); // number of pending logging events should not increase
    }
    else {
      // Note, we MUST avoid SYNCHRONOUS invokeAndWait to prevent deadlocks
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        try {
          appendToLoggers(event, LOGGERS);
        }
        finally {
          myPendingAppendCounts.decrementAndGet();
        }
      });
    }
  }

  void appendToLoggers(@NotNull LoggingEvent event, @NotNull ErrorLogger[] errorLoggers) {
    if (myDialogRunnable != null) {
      return;
    }

    IdeaLoggingEvent ideaEvent;
    Object messageObject = event.getMessage();
    if (messageObject instanceof IdeaLoggingEvent) {
      ideaEvent = (IdeaLoggingEvent)messageObject;
    }
    else {
      ThrowableInformation info = event.getThrowableInformation();
      if (info == null || info.getThrowable() == null) return;
      ideaEvent = extractLoggingEvent(messageObject, info.getThrowable());
    }

    for (int i = errorLoggers.length - 1; i >= 0; i--) {
      ErrorLogger logger = errorLoggers[i];
      if (!logger.canHandle(ideaEvent)) {
        continue;
      }
      //noinspection NonAtomicOperationOnVolatileField
      myDialogRunnable = () -> {
        try {
          logger.handle(ideaEvent);
        }
        finally {
          myDialogRunnable = null;
        }
      };
      Application app = ApplicationManager.getApplication();
      if (app == null) {
        new Thread(myDialogRunnable, "dialog appender logger").start();
      }
      else {
        app.executeOnPooledThread(myDialogRunnable);
      }
      break;
    }
  }

  @SuppressWarnings("deprecation")
  private static IdeaLoggingEvent extractLoggingEvent(Object messageObject, Throwable throwable) {
    Throwable rootCause = ExceptionUtil.getRootCause(throwable);
    if (rootCause instanceof LogEventException) {
      return ((LogEventException)rootCause).getLogMessage();
    }

    String message = null;
    ExceptionWithAttachments withAttachments = ExceptionUtil.findCause(throwable, ExceptionWithAttachments.class);
    if (withAttachments instanceof RuntimeExceptionWithAttachments) {
      message = ((RuntimeExceptionWithAttachments)withAttachments).getUserMessage();
    }
    if (message == null && messageObject != null) {
      message = messageObject.toString();
    }
    if (withAttachments != null) {
      return LogMessage.createEvent(throwable, message, withAttachments.getAttachments());
    }
    else {
      return new IdeaLoggingEvent(message, throwable);
    }
  }

  @TestOnly
  Runnable getDialogRunnable() {
    return myDialogRunnable;
  }

  @Override
  public boolean requiresLayout() {
    return false;
  }

  @Override
  public void close() { }
}