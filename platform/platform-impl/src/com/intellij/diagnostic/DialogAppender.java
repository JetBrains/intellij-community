// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.idea.Main;
import com.intellij.openapi.diagnostic.*;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class DialogAppender extends AppenderSkeleton {
  private static final ErrorLogger[] LOGGERS = {new DefaultIdeaErrorLogger()};
  private static final int MAX_EARLY_LOGGING_EVENTS = 5;
  private static final int MAX_ASYNC_LOGGING_EVENTS = 5;

  private final Queue<IdeaLoggingEvent> myEarlyEvents = new ArrayDeque<>();
  private final AtomicInteger myPendingAppendCounts = new AtomicInteger();
  private volatile Runnable myDialogRunnable;

  @Override
  protected synchronized void append(@NotNull LoggingEvent event) {
    if (!event.getLevel().isGreaterOrEqual(Level.ERROR) || Main.isCommandLine()) {
      return;  // the dialog appender doesn't deal with non-critical errors and is meaningless when there is no frame to show an error icon
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

    if (LoadingState.COMPONENTS_LOADED.isOccurred()) {
      IdeaLoggingEvent queued;
      while ((queued = myEarlyEvents.poll()) != null) queueAppend(queued);
      queueAppend(ideaEvent);
    }
    else if (myEarlyEvents.size() < MAX_EARLY_LOGGING_EVENTS) {
      myEarlyEvents.add(ideaEvent);
    }
  }

  private void queueAppend(IdeaLoggingEvent event) {
    if (myPendingAppendCounts.incrementAndGet() > MAX_ASYNC_LOGGING_EVENTS) {
      // Stop adding requests to the queue or we can get OOME on pending logging requests (IDEA-95327)
      myPendingAppendCounts.decrementAndGet(); // number of pending logging events should not increase
    }
    else {
      // Note, we MUST avoid SYNCHRONOUS invokeAndWait to prevent deadlocks
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

  void appendToLoggers(@NotNull IdeaLoggingEvent event, ErrorLogger @NotNull [] errorLoggers) {
    if (myDialogRunnable != null) {
      return;
    }

    for (int i = errorLoggers.length - 1; i >= 0; i--) {
      ErrorLogger logger = errorLoggers[i];
      if (!logger.canHandle(event)) {
        continue;
      }
      //noinspection NonAtomicOperationOnVolatileField
      myDialogRunnable = () -> {
        try {
          logger.handle(event);
        }
        finally {
          myDialogRunnable = null;
        }
      };
      AppExecutorUtil.getAppExecutorService().execute(myDialogRunnable);
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
    List<ExceptionWithAttachments> withAttachments = ExceptionUtil.findCauseAndSuppressed(throwable, ExceptionWithAttachments.class);
    if (!withAttachments.isEmpty() && withAttachments.get(0) instanceof RuntimeExceptionWithAttachments) {
      message = ((RuntimeExceptionWithAttachments)withAttachments.get(0)).getUserMessage();
    }
    if (message == null && messageObject != null) {
      message = messageObject.toString();
    }
    if (!withAttachments.isEmpty()) {
      return LogMessage.createEvent(
        throwable, message,
        withAttachments.stream().flatMap(e -> Stream.of(e.getAttachments())).toArray(Attachment[]::new));
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
