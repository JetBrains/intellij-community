// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

/**
 * Allows recording the latency of executing editor actions.
 *
 * @author yole
 */
public interface LatencyRecorder {
  /**
   * Records the timestamp of an action that should be reflected in the typing latency report (affects the editor and does not show any
   * additional modal UI)
   * @param editor the editor in which the action was executed.
   * @param actionId the ID to include in the report
   * @param timestampMs Timestamp (System.currentTimeMillis) when the event triggering the action has occurred
   */
  void recordLatencyAwareAction(@NotNull Editor editor, @NotNull String actionId, long timestampMs);

  static LatencyRecorder getInstance() {
    return ApplicationManager.getApplication().getService(LatencyRecorder.class);
  }
}
