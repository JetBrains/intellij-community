/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.diagnostic.VMOptions;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import org.jetbrains.annotations.NotNull;

public class AndroidStudioSystemHealthMonitorAdapter {

  public static void countActionInvocation(AnAction anAction, Presentation presentation, AnActionEvent event) {
    var ourListener = EventsListener.getInstance();
    if (ourListener != null) {
      ourListener.countActionInvocation(anAction, presentation, event);
    }
  }

  public static boolean handleExceptionEvent(IdeaLoggingEvent event, VMOptions.MemoryKind memoryKind) {
    var ourListener = EventsListener.getInstance();
    if (ourListener != null) {
      return ourListener.handleExceptionEvent(event, memoryKind);
    } else {
      return false;
    }
  }

  public interface EventsListener {

    @SuppressWarnings("IncorrectServiceRetrieving") // EventsListener is registered elsewhere (in the Android plugin).
    private static EventsListener getInstance() {
      return ApplicationManager.getApplication().getService(EventsListener.class);
    }

    void countActionInvocation(AnAction aClass, Presentation presentation, AnActionEvent event);

    boolean handleExceptionEvent(IdeaLoggingEvent event, VMOptions.MemoryKind memoryKind);
  }
}
