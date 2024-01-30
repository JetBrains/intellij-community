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

public class NullAndroidStudioAnalytics extends AndroidStudioAnalytics {
  public static final NullAndroidStudioAnalytics INSTANCE = new NullAndroidStudioAnalytics();

  @Override
  public void recordHighlightingLatency(Document document, long latencyMs) {

  }

  @Override
  public void logUpdateDialogOpenManually(@NotNull String newBuild) {

  }

  @Override
  public void logNotificationShown(@NotNull String newBuild) {

  }

  @Override
  public void logClickNotification(@NotNull String newBuild) {

  }

  @Override
  public void logUpdateDialogOpenFromNotification(@NotNull String newBuild) {

  }

  @Override
  public void logClickIgnore(String code) {

  }

  @Override
  public void logClickLater(String code) {

  }

  @Override
  public void logDownloadSuccess(String code) {

  }

  @Override
  public void logDownloadFailure(String code) {

  }

  @Override
  public void updateAndroidStudioMetrics() {

  }

  @Override
  public void initializeAndroidStudioUsageTrackerAndPublisher() {

  }

  @Override
  public boolean isAllowed() {
    return false;
  }
}
