/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;

/**
 * Interface for actions activated by keystrokes in the editor.
 *
 * @see EditorActionManager#setActionHandler(String, EditorActionHandler)
 */
public abstract class EditorActionHandler {
  /**
   * Checks if the action handler is currently enabled.
   *
   * @param editor      the editor in which the action is invoked.
   * @param dataContext the data context for the action.
   * @return true if the action is enabled, false otherwise
   */
  public boolean isEnabled(Editor editor, DataContext dataContext) {
    return true;
  }

  /**
   * Executes the action.
   *
   * @param editor      the editor in which the action is invoked.
   * @param dataContext the data context for the action.
   */
  public abstract void execute(Editor editor, DataContext dataContext);

  public boolean executeInCommand(Editor editor, DataContext dataContext) {
    return true;
  }

  public DocCommandGroupId getCommandGroupId(Editor editor) {
    // by default avoid merging two consequential commands, and, in the same time, pass along the Document
    return DocCommandGroupId.noneGroupId(editor.getDocument());
  }
}
