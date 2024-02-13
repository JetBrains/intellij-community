// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.idea.AppMode;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.ApiStatus;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

@ApiStatus.Internal
public final class DialogAppender extends Handler {
  private static final int MAX_EARLY_LOGGING_EVENTS = 5;

  private static volatile boolean ourDelay;

  private int myEarlyEventCounter;
  private final Queue<IdeaLoggingEvent> myEarlyEvents = new ArrayDeque<>();
  private final ScheduledExecutorService myExecutor = AppExecutorUtil.createBoundedScheduledExecutorService("DialogAppender", 1);

  //TODO android update checker accesses project jdk, fix it and remove
  public static void delayPublishingForcibly() {
    ourDelay = true;
  }

  public static void stopForceDelaying() {
    ourDelay = false;
  }

  @Override
  public void publish(LogRecord event) {
    if (event.getLevel().intValue() < Level.SEVERE.intValue() || AppMode.isCommandLine()) {
      // the dialog appender doesn't deal with non-critical errors
      // also, it makes no sense when there is no frame to show an error icon
      return;
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

    synchronized (this) {
      if (LoadingState.COMPONENTS_LOADED.isOccurred() && !ourDelay) {
        processEarlyEventsIfNeeded();
        queueAppend(ideaEvent);
      }
      else {
        myEarlyEventCounter++;
        if (myEarlyEvents.size() < MAX_EARLY_LOGGING_EVENTS) {
          myEarlyEvents.add(ideaEvent);
        }
      }
    }
  }

  private void processEarlyEventsIfNeeded() {
    if (myEarlyEventCounter == 0) return;
    IdeaLoggingEvent queued;
    while ((queued = myEarlyEvents.poll()) != null) {
      myEarlyEventCounter--;
      queueAppend(queued);
    }
    if (myEarlyEventCounter > 0) {
      queueAppend(new IdeaLoggingEvent(DiagnosticBundle.message("error.monitor.early.errors.skipped", myEarlyEventCounter), new Throwable()));
    }
  }

  private void queueAppend(IdeaLoggingEvent event) {
    if (DefaultIdeaErrorLogger.canHandle(event)) {
      myExecutor.execute(() -> DefaultIdeaErrorLogger.handle(event));
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
    if (withAttachments.isEmpty()) {
      return new IdeaLoggingEvent(message, throwable);
    }
    else {
      List<Attachment> list = new ArrayList<>();
      for (ExceptionWithAttachments e : withAttachments) {
        Collections.addAll(list, e.getAttachments());
      }
      return LogMessage.eventOf(throwable, message, list);
    }
  }

  @Override
  public void flush() { }

  @Override
  public void close() { }
}
