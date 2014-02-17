/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretAction;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for actions activated by keystrokes in the editor.
 * Implementations should override {@link #execute(com.intellij.openapi.editor.Editor, com.intellij.openapi.actionSystem.DataContext)} or
 * {@link #execute(com.intellij.openapi.editor.Editor, com.intellij.openapi.editor.Caret, com.intellij.openapi.actionSystem.DataContext)}
 * (preferrably).
 * <p>
 * Two types of handlers are supported: the ones which are executed once, and the ones which are executed for each caret. The latter can be
 * created using {@link com.intellij.openapi.editor.actionSystem.EditorActionHandler#EditorActionHandler(boolean)} constructor.
 *
 * @see EditorActionManager#setActionHandler(String, EditorActionHandler)
 */
public abstract class EditorActionHandler {
  private final boolean myRunForEachCaret;
  private boolean inExecution;

  protected EditorActionHandler() {
    this(false);
  }

  protected EditorActionHandler(boolean runForEachCaret) {
    myRunForEachCaret = runForEachCaret;
  }

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
   * Executes the action in the context of the current caret. This method exists for historical reasons, in most cases you should use
   * {@link #execute(com.intellij.openapi.editor.Editor, com.intellij.openapi.editor.Caret, com.intellij.openapi.actionSystem.DataContext)}
   * instead.
   *
   * @param editor      the editor in which the action is invoked.
   * @param dataContext the data context for the action.
   *
   * @see {@link com.intellij.openapi.editor.CaretModel#getCurrentCaret()}
   */
  public void execute(Editor editor, DataContext dataContext) {
    if (inExecution) {
      return;
    }
    try {
      inExecution = true;
      execute(editor, editor.getCaretModel().getCurrentCaret(), dataContext);
    }
    finally {
      inExecution = false;
    }
  }

  /**
   * Executes the action in the context of given caret
   *
   * @param editor      the editor in which the action is invoked.
   * @param caret       the caret for which the action is performed at the moment or <code>null</code> if it's a 'one-off' action executed
   *                    without current context
   * @param dataContext the data context for the action.
   */
  public void execute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (inExecution) {
      return;
    }
    try {
      inExecution = true;
      execute(editor, dataContext);
    }
    finally {
      inExecution = false;
    }
  }

  public boolean executeInCommand(Editor editor, DataContext dataContext) {
    return true;
  }

  public boolean runForAllCarets() {
    return myRunForEachCaret;
  }

  /**
   * Executes the action in the context of given caret. If the caret is <code>null</code>, and the handler is a 'per-caret' handler,
   * it's executed for all carets.
   *
   * @param editor      the editor in which the action is invoked.
   * @param dataContext the data context for the action.
   */
  public void executeInCaretContext(final Editor editor, Caret caret, final DataContext dataContext) {
    if (caret == null && runForAllCarets()) {
      editor.getCaretModel().runForEachCaret(new CaretAction() {
        @Override
        public void perform(Caret caret) {
          execute(editor, caret, dataContext);
        }
      });
    }
    else {
      execute(editor, caret, dataContext);
    }
  }

  public DocCommandGroupId getCommandGroupId(Editor editor) {
    // by default avoid merging two consequential commands, and, in the same time, pass along the Document
    return DocCommandGroupId.noneGroupId(editor.getDocument());
  }
}
