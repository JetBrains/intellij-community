// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
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
   * @param event the event which initiated the action
   */
  void recordLatencyAwareAction(@NotNull Editor editor, @NotNull String actionId, @NotNull AnActionEvent event);

  static LatencyRecorder getInstance() {
    return ServiceManager.getService(LatencyRecorder.class);
  }
}
