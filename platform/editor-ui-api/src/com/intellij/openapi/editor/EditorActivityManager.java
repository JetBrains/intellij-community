// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A way to abstract various properties of an editor (as being visible to a user) away from Swing.
 * Useful in scenarios where an application is headless
 * or the editor is not physically visible but we want it to treated as if it's (a guest session during collaborative development)
 */
@ApiStatus.Experimental
public class EditorActivityManager {
  public static EditorActivityManager getInstance() {
    return ApplicationManager.getApplication().getService(EditorActivityManager.class);
  }

  /**
   * Determines whether an editor is visible to a user
   */
  public boolean isVisible(@NotNull Editor editor) {
    return ApplicationManager.getApplication().isHeadlessEnvironment() || editor.getContentComponent().isShowing();
  }

  /**
   * Determines whether an editor has focus
   */
  public boolean isFocused(@NotNull Editor editor) {
    return ApplicationManager.getApplication().isHeadlessEnvironment() || editor.getContentComponent().hasFocus();
  }
}
