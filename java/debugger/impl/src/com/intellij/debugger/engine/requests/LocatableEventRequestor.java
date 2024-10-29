// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.requests;

import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.openapi.util.NlsContexts;
import com.sun.jdi.event.LocatableEvent;
import org.jetbrains.annotations.NotNull;

public interface LocatableEventRequestor extends SuspendingRequestor {
  /**
   * @return true if request was hit by the event, false otherwise
   */
  boolean processLocatableEvent(@NotNull SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException;

  default boolean shouldIgnoreThreadFiltering() {
    return false;
  }

  class EventProcessingException extends Exception {
    private final @NlsContexts.DialogTitle String myTitle;

    public EventProcessingException(@NlsContexts.DialogTitle String title, String message, Throwable cause) {
      super(message, cause);
      myTitle = title;
    }

    public @NlsContexts.DialogTitle String getTitle() {
      return myTitle;
    }
  }
}
