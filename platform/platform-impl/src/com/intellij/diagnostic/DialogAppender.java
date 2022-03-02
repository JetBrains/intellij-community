// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.idea.Main;
import com.intellij.openapi.diagnostic.*;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

public final class DialogAppender extends Handler {
  private static final ErrorLogger[] LOGGERS = {new DefaultIdeaErrorLogger()};
  private static final int MAX_EARLY_LOGGING_EVENTS = 5;
  private static final int MAX_ASYNC_LOGGING_EVENTS = 5;

  private final Queue<IdeaLoggingEvent> myEarlyEvents = new ArrayDeque<>();
  private final AtomicInteger myPendingAppendCounts = new AtomicInteger();
  private volatile Runnable myDialogRunnable;

  @Override
  public synchronized void publish(LogRecord event) {
    if (event.getLevel().intValue() < Level.SEVERE.intValue() || Main.isCommandLine()) {
      return;  // the dialog appender doesn't deal with non-critical errors and is meaningless when there is no frame to show an error icon
    }

    IdeaLoggingEvent ideaEvent;
    Object[] parameters = event.getParameters();
    if (parameters != null && parameters.length > 0 && parameters[0] instanceof IdeaLoggingEvent) {
      ideaEvent = (IdeaLoggingEvent)parameters[0];
    }
    else {
      Throwable thrown = event.getThrown();
      if (thrown == null) return;
      ideaEvent = extractLoggingEvent(event.getMessage(), thrown);
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
      // stop adding requests to the queue, or we can get OOME on pending logging requests (IDEA-95327)
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

  private static IdeaLoggingEvent extractLoggingEvent(Object messageObject, Throwable throwable) {
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
  public void flush() {
  }

  @Override
  public void close() { }
}
