// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal API, please don't use.
 * <p>
 * For reporting errors, see {@link com.intellij.openapi.diagnostic.Logger#error} methods.
 * <p>
 * For receiving reports, register own {@link com.intellij.openapi.diagnostic.ErrorReportSubmitter}.
 */
@ApiStatus.Internal
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
      message = event.getData() instanceof AbstractMessage am ? am : new LogMessage(event.getThrowable(), event.getMessage(), List.of());
    }
    else if (myErrors.size() == MAX_POOL_SIZE) {
      message = new LogMessage(new TooManyErrorsException(), null, List.of());
    }
    else {
      return;
    }
    doAddMessage(message);
  }

  public @NotNull State getState() {
    if (myErrors.isEmpty()) return State.NoErrors;
    for (var message: myErrors) {
      if (!message.isRead()) return State.UnreadErrors;
    }
    return State.ReadErrors;
  }

  public List<AbstractMessage> getFatalErrors(boolean includeReadMessages, boolean includeSubmittedMessages) {
    var result = new ArrayList<AbstractMessage>();
    for (var message : myErrors) {
      if (!includeReadMessages && message.isRead()) continue;
      if (!includeSubmittedMessages && (message.isSubmitted() || message.getThrowable() instanceof TooManyErrorsException)) continue;
      result.add(message);
    }
    return result;
  }

  public void clearErrors() {
    for (var message : myErrors) {
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
    for (var listener : myListeners) {
      if (!listener.beforeEntryAdded(message)) {
        return;
      }
    }

    if (ApplicationManager.getApplication().isInternal()) {
      message.getAllAttachments().forEach(attachment -> attachment.setIncluded(true));
    }

    if (shallAddSilently(message)) {
      message.setRead(true);
    }

    message.setOnReadCallback(() -> notifyEntryRead());
    myErrors.add(message);
    notifyEntryAdded();
  }

  private static boolean shallAddSilently(AbstractMessage message) {
    return SlowOperations.isMyMessage(message.getThrowable().getMessage());
  }

  public static final class TooManyErrorsException extends Exception {
    private TooManyErrorsException() {
      super(DiagnosticBundle.message("error.monitor.too.many.errors"));
    }
  }
}
