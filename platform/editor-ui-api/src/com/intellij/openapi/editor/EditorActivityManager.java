// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @deprecated Use {@link UIUtil#hasFocus(Component)} or {@link UIUtil#isShowing(Component)} directly}
 */
@Deprecated
public class EditorActivityManager {
  public static EditorActivityManager getInstance() {
    return ApplicationManager.getApplication().getService(EditorActivityManager.class);
  }

  /**
   * Determines whether an editor is visible to a user
   */
  public boolean isVisible(@NotNull Editor editor) {
    return UIUtil.isShowing(editor.getContentComponent());
  }

  /**
   * Determines whether an editor has focus
   */
  public boolean isFocused(@NotNull Editor editor) {
    return UIUtil.hasFocus(editor.getContentComponent());
  }

  /**
   * Determines whether a fileEditor is visible to a user
   */
  public boolean isVisible(@NotNull FileEditor fileEditor) {
    return UIUtil.isShowing(fileEditor.getComponent());
  }

  /**
   * Determines whether a fileEditor has focus
   */
  public boolean isFocused(@NotNull FileEditor fileEditor) {
    return UIUtil.hasFocus(fileEditor.getComponent());
  }
}
