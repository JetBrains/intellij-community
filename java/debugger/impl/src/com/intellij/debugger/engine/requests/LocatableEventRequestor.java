package com.intellij.debugger.engine.requests;

import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.requests.Requestor;
import com.sun.jdi.event.LocatableEvent;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jun 27, 2003
 * Time: 8:05:52 PM
 * To change this template use Options | File Templates.
 */
public interface LocatableEventRequestor extends Requestor {
  /**
   * @returns true if requesto was hit by the event, false otherwise
   */ 
  boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException;

  /**
   * @return either DebuggerSettings.SUSPEND_NONE or DebuggerSettings.SUSPEND_ALL or DebuggerSettings.SUSPEND_THREAD
   */
  String getSuspendPolicy();

  class EventProcessingException extends Exception {
    private final String myTitle;

    public EventProcessingException(String title, String message, Throwable cause) {
      super(message, cause);
      myTitle = title;
    }

    public String getTitle() {
      return myTitle;
    }
  }
}
