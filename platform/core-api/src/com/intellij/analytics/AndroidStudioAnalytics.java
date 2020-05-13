/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.intellij.analytics;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

abstract public class AndroidStudioAnalytics {
  static AndroidStudioAnalytics INSTANCE;

  public static void initialize(AndroidStudioAnalytics analytics) {
    INSTANCE = analytics;
  }

  public static AndroidStudioAnalytics getInstance() {
    if (INSTANCE == null) {
      // Android Studio Developers: If you hit this exception, you're trying to find out the status
      // of AnalyticsSettings before the system has been initialized. Please reach out the the owners
      // of this code to figure out how best to do these checks instead of getting null values.
      throw new RuntimeException("call to AndroidStudioAnalytics before initialization");
    }
    return INSTANCE;
  }

  public abstract void recordHighlightingLatency(Document document, long latencyMs);

  public abstract void logUpdateDialogOpenManually(@NotNull String newBuild);

  public abstract void logNotificationShown(@NotNull String newBuild);

  public abstract void logClickNotification(@NotNull String newBuild);

  public abstract void logUpdateDialogOpenFromNotification(@NotNull String newBuild);

  public abstract void logClickIgnore(String code);

  public abstract void logClickLater(String code);

  public abstract void logDownloadSuccess(String code);

  public abstract void logDownloadFailure(String code);

  public abstract void updateAndroidStudioMetrics();

  public abstract void initializeAndroidStudioUsageTrackerAndPublisher();

  public abstract boolean isAllowed();
}
