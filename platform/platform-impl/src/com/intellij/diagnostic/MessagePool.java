// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MessagePool {
  public enum State { NoErrors, ReadErrors, UnreadErrors }

  private static final int MAX_POOL_SIZE = 100;

  private static final class MessagePoolHolder {
    private static final MessagePool ourInstance = new MessagePool();
  }

  public static MessagePool getInstance() {
    return MessagePoolHolder.ourInstance;
  }

  private final List<AbstractMessage> myErrors = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<MessagePoolListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private MessagePool() { }

  public void addIdeFatalMessage(@NotNull IdeaLoggingEvent event) {
    AbstractMessage message;
    if (myErrors.size() < MAX_POOL_SIZE) {
      Object data = event.getData();
      message = data instanceof AbstractMessage ? (AbstractMessage)data :
                new LogMessage(event.getThrowable(), event.getMessage(), Collections.emptyList());
    }
    else if (myErrors.size() == MAX_POOL_SIZE) {
      message = new LogMessage(new TooManyErrorsException(), null, Collections.emptyList());
    }
    else {
      return;
    }
    doAddMessage(message);
  }

  public @NotNull State getState() {
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

  private void doAddMessage(@NotNull AbstractMessage message) {
    message.setOnReadCallback(() -> notifyEntryRead());
    if (ApplicationManager.getApplication().isInternal()) {
      for (Attachment attachment : message.getAllAttachments()) {
        attachment.setIncluded(true);
      }
    }
    if (SlowOperations.isMyMessage(message.getThrowable().getMessage())) {
      message.setRead(true);
    }
    myErrors.add(message);
    notifyEntryAdded();
  }

  public static final class TooManyErrorsException extends Exception {
    private TooManyErrorsException() {
      super(DiagnosticBundle.message("error.monitor.too.many.errors"));
    }
  }
}
