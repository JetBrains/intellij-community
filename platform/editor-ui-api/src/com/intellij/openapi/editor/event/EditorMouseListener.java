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
 * Allows to receive information about mouse clicks in an editor.
 *
 * @see com.intellij.openapi.editor.Editor#addEditorMouseListener(EditorMouseListener)
 * @see EditorEventMulticaster#addEditorMouseListener(EditorMouseListener)
 * @see EditorMouseMotionListener
 */
public interface EditorMouseListener extends EventListener {

  /**
   * Called when a mouse button is pressed over the editor.
   * <p/>
   * <b>Note:</b> this callback is assumed to be at the very start of 'mouse press' processing, i.e. common actions
   * like 'caret position change', 'selection change' etc implied by the 'mouse press' have not been performed yet.
   *
   * @param e the event containing information about the mouse press.
   */
  void mousePressed(EditorMouseEvent e);

  /**
   * Called when a mouse button is clicked over the editor.
   *
   * @param e the event containing information about the mouse click.
   */
  void mouseClicked(EditorMouseEvent e);

  /**
   * Called when a mouse button is released over the editor.
   *
   * @param e the event containing information about the mouse release.
   */
  void mouseReleased(EditorMouseEvent e);

  /**
   * Called when the mouse enters the editor.
   *
   * @param e the event containing information about the mouse movement.
   */
  void mouseEntered(EditorMouseEvent e);

  /**
   * Called when the mouse exits the editor.
   *
   * @param e the event containing information about the mouse movement.
   */
  void mouseExited(EditorMouseEvent e);
}
