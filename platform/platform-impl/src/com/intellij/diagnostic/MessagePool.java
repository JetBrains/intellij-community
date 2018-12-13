// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MessagePool {
  public enum State { NoErrors, ReadErrors, UnreadErrors }

  private static final int MAX_POOL_SIZE = 100;
  private static final int MAX_GROUP_SIZE = 20;
  private static final int GROUP_TIME_SPAN_MS = 1000;

  private static class MessagePoolHolder {
    private static final MessagePool ourInstance = new MessagePool();
  }

  public static MessagePool getInstance() {
    return MessagePoolHolder.ourInstance;
  }

  private final List<AbstractMessage> myErrors = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<MessagePoolListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final MessageGrouper myGrouper = new MessageGrouper();

  private MessagePool() { }

  public void addIdeFatalMessage(@NotNull IdeaLoggingEvent event) {
    if (myErrors.size() < MAX_POOL_SIZE) {
      Object data = event.getData();
      if (data instanceof AbstractMessage) {
        myGrouper.addToGroup((AbstractMessage)data);
      }
      else {
        myGrouper.addToGroup(new LogMessage(event.getThrowable(), event.getMessage(), Collections.emptyList()));
      }
    }
    else if (myErrors.size() == MAX_POOL_SIZE) {
      TooManyErrorsException e = new TooManyErrorsException();
      myGrouper.addToGroup(new LogMessage(e, null, Collections.emptyList()));
    }
  }

  public State getState() {
    if (myErrors.isEmpty()) return State.NoErrors;
    for (AbstractMessage message: myErrors) {
      if (!message.isRead()) return State.UnreadErrors;
    }
    return State.ReadErrors;
  }

  public List<AbstractMessage> getFatalErrors(boolean includeReadMessages, boolean includeSubmittedMessages) {
    List<AbstractMessage> result = new ArrayList<>();
    for (AbstractMessage message : myErrors) {
      if ((!message.isRead() && !message.isSubmitted()) ||
          (message.isRead() && includeReadMessages) ||
          (message.isSubmitted() && includeSubmittedMessages)) {
        result.add(message);
      }
    }
    return result;
  }

  public void clearErrors() {
    for (AbstractMessage message : myErrors) {
      message.setRead(true); // expire notifications
    }
    myErrors.clear();
    notifyPoolCleared();
  }

  public void addListener(MessagePoolListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(MessagePoolListener listener) {
    myListeners.remove(listener);
  }

  private void notifyEntryAdded() {
    myListeners.forEach(MessagePoolListener::newEntryAdded);
  }

  private void notifyPoolCleared() {
    myListeners.forEach(MessagePoolListener::poolCleared);
  }

  private void notifyEntryRead() {
    myListeners.forEach(MessagePoolListener::entryWasRead);
  }

  private class MessageGrouper implements Runnable {
    private final List<AbstractMessage> myMessages = new ArrayList<>();
    private Future<?> myAlarm = CompletableFuture.completedFuture(null);

    @Override
    public void run() {
      synchronized (myMessages) {
        if (myMessages.size() > 0) {
          post();
        }
      }
    }

    private void post() {
      AbstractMessage message = myMessages.size() == 1 ? myMessages.get(0) : groupMessages();
      message.setOnReadCallback(() -> notifyEntryRead());
      myMessages.clear();
      myErrors.add(message);
      notifyEntryAdded();
    }

    private AbstractMessage groupMessages() {
      AbstractMessage first = myMessages.get(0);

      List<Attachment> attachments = new ArrayList<>(first.getAllAttachments());
      StringBuilder stacktraces = new StringBuilder("Following exceptions happened soon after this one, most probably they are induced.");
      SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
      for (int i = 1; i < myMessages.size(); i++) {
        AbstractMessage next = myMessages.get(i);
        stacktraces.append("\n\n\n").append(format.format(next.getDate())).append('\n');
        if (!StringUtil.isEmptyOrSpaces(next.getMessage())) stacktraces.append(next.getMessage()).append('\n');
        stacktraces.append(next.getThrowableText());
      }
      attachments.add(new Attachment("induced.txt", stacktraces.toString()));

      return new LogMessage(first.getThrowable(), first.getMessage(), attachments);
    }

    private void addToGroup(@NotNull AbstractMessage message) {
      synchronized (myMessages) {
        myMessages.add(message);
        if (myMessages.size() >= MAX_GROUP_SIZE) {
          post();
        }
        else {
          myAlarm.cancel(false);
          myAlarm = AppExecutorUtil.getAppScheduledExecutorService().schedule(this, GROUP_TIME_SPAN_MS, TimeUnit.MILLISECONDS);
        }
      }
    }
  }

  public static class TooManyErrorsException extends Exception {
    private TooManyErrorsException() {
      super(DiagnosticBundle.message("error.monitor.too.many.errors"));
    }
  }
}