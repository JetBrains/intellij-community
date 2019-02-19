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

import com.android.annotations.NonNull;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;

public class AndroidStudioSystemHealthMonitorAdapter {

  private static EventsListener ourListener;

  public static void countActionInvocation(Class<? extends AnAction> aClass, Presentation presentation, AnActionEvent event) {
    if (ourListener != null) {
      ourListener.countActionInvocation(aClass, presentation, event);
    }
  }

  public static void incrementAndSaveBundledPluginsExceptionCount() {
    if (ourListener != null) {
      ourListener.incrementAndSaveBundledPluginsExceptionCount();
    }
  }

  public static void incrementAndSaveExceptionCount() {
    if (ourListener != null) {
      ourListener.incrementAndSaveExceptionCount();
    }
  }


  public static void incrementAndSaveNonBundledPluginsExceptionCount() {
    if (ourListener != null) {
      ourListener.incrementAndSaveNonBundledPluginsExceptionCount();
    }
  }

  public static void recordWriteLockWaitTime(long elapsed) {
    if (ourListener != null) {
      ourListener.recordWriteLockWaitTime(elapsed);
    }
  }

  public static void reportException(Throwable t, StackTrace trace) {
    if (ourListener != null) {
      ourListener.reportException(t, trace);
    }
  }


  public static void registerEventsListener(@NonNull EventsListener listener) {
    assert ourListener == null;
    ourListener = listener;
  }

  public static void unregisterEventsListener(@NonNull EventsListener listener) {
    assert ourListener == listener;
    ourListener = null;
  }

  public interface EventsListener {
    void countActionInvocation(Class<? extends AnAction> aClass, Presentation presentation, AnActionEvent event);

    void incrementAndSaveBundledPluginsExceptionCount();

    void incrementAndSaveExceptionCount();

    void incrementAndSaveNonBundledPluginsExceptionCount();

    void reportException(Throwable t, StackTrace trace);

    void recordWriteLockWaitTime(long elapsed);
  }
}
