// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.event;

import java.util.EventListener;

/**
 * Allows to receive notifications about mouse movement in the editor.
 *
 * @see com.intellij.openapi.editor.Editor#addEditorMouseMotionListener(EditorMouseMotionListener)
 * @see EditorEventMulticaster#addEditorMouseMotionListener(EditorMouseMotionListener)
 * @see EditorMouseListener
 */
public interface EditorMouseMotionListener extends EventListener {
  /**
   * Called when the mouse is moved over the editor and no mouse buttons are pressed.
   *
   * @param e the event containing information about the mouse movement.
   */
  default void mouseMoved(EditorMouseEvent e) {
  }

  /**
   * Called when the mouse is moved over the editor and a mouse button is pressed.
   *
   * @param e the event containing information about the mouse movement.
   */
  default void mouseDragged(EditorMouseEvent e) {
  }
}
