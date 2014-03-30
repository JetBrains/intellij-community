/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  void mouseMoved(EditorMouseEvent e);

  /**
   * Called when the mouse is moved over the editor and a mouse button is pressed.
   *
   * @param e the event containing information about the mouse movement.
   */
  void mouseDragged(EditorMouseEvent e);
}
