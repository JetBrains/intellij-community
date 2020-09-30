// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.requests;

import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.requests.Requestor;
import com.intellij.openapi.util.NlsContexts;
import com.sun.jdi.event.LocatableEvent;
import org.jetbrains.annotations.NotNull;

public interface LocatableEventRequestor extends Requestor {
  /**
   * @returns true if request was hit by the event, false otherwise
   */ 
  boolean processLocatableEvent(@NotNull SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException;

  /**
   * @return either DebuggerSettings.SUSPEND_NONE or DebuggerSettings.SUSPEND_ALL or DebuggerSettings.SUSPEND_THREAD
   */
  String getSuspendPolicy();

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
