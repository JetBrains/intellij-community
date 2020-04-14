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
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import org.jetbrains.annotations.NotNull;

public class AndroidStudioSystemHealthMonitorAdapter {

  private static EventsListener ourListener;

  public static void countActionInvocation(Class<? extends AnAction> aClass, Presentation presentation, AnActionEvent event) {
    if (ourListener != null) {
      ourListener.countActionInvocation(aClass, presentation, event);
    }
  }

  public static void recordWriteLockWaitTime(long elapsed) {
    if (ourListener != null) {
      ourListener.recordWriteLockWaitTime(elapsed);
    }
  }

  public static boolean handleExceptionEvent(IdeaLoggingEvent event, VMOptions.MemoryKind memoryKind) {
    if (ourListener != null) {
      return ourListener.handleExceptionEvent(event, memoryKind);
    } else {
      return false;
    }
  }

  public static void registerEventsListener(@NotNull EventsListener listener) {
    if (ourListener != null) {
      throw new IllegalStateException("listener already registered");
    }
    ourListener = listener;
  }

  public interface EventsListener {
    void countActionInvocation(Class<? extends AnAction> aClass, Presentation presentation, AnActionEvent event);

    void recordWriteLockWaitTime(long elapsed);

    boolean handleExceptionEvent(IdeaLoggingEvent event, VMOptions.MemoryKind memoryKind);
  }
}
