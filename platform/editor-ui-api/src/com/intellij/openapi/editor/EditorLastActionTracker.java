// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.Nullable;

/**
 * This component provides the notion of last editor action.
 * Its purpose is to be able to determine whether some action was performed right after another specific action.
 * <p>
 * It's supposed to be used from EDT only.
 */
public interface EditorLastActionTracker {
  public static EditorLastActionTracker getInstance() {
    return ServiceManager.getService(EditorLastActionTracker.class);
  }

  /**
   * Returns the id of the previously invoked action or {@code null}, if no history exists yet, or last user activity was of
   * non-action type, like mouse clicking in editor or text typing, or previous action was invoked for a different editor.
   */
  @Nullable
  String getLastActionId();
}
