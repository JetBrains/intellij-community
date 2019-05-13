/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.engine.requests;

import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.requests.Requestor;
import com.sun.jdi.event.LocatableEvent;

public interface LocatableEventRequestor extends Requestor {
  /**
   * @returns true if request was hit by the event, false otherwise
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
