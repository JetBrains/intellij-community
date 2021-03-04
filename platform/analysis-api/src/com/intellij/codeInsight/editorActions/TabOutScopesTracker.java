// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

/**
 * This service keeps track of scopes inside quotes or brackets in editor, which user can exit (moving caret just to the right of closing
 * quote/bracket) using Tab key. The scope is usually created (registered) when a pair of quotes/brackets is inserted into a document, and
 * lives until user 'tabs out' of this scope, or performs editing outside it.
 */
public interface TabOutScopesTracker {
  static TabOutScopesTracker getInstance() {
    return ApplicationManager.getApplication().getService(TabOutScopesTracker.class);
  }

  /**
   * Registers a new scope (empty at the time of call) at caret offset. Caret is supposed to be located between just inserted pair
   * of quotes/brackets.
   */
  default void registerEmptyScopeAtCaret(@NotNull Editor editor) {
    registerEmptyScope(editor, editor.getCaretModel().getOffset());
  }

  /**
   * Registers a new scope for a range. Tab out of this scope will place the caret right after {@code rangeEnd}
   *
   * @param editor editor
   * @param rangeStart the start of the registered scope
   * @param rangeEnd the end of the registered scope
   */
  default void registerScopeRange(@NotNull Editor editor, final int rangeStart, final int rangeEnd) {
    registerScopeRange(editor, rangeStart, rangeEnd, rangeEnd + 1);
  }

  /**
   * Registers a new scope for a range with the exact location where to place the caret after tab-out.
   * {@code tabOutOffset} has to be greater than {@code rangeEnd}
   *
   * @param editor instance of the editor
   * @param rangeStart the start of the registered scope
   * @param rangeEnd the end of the registered scope
   * @param tabOutOffset the absolute offset in the document where to place the caret after tab-out.
   */
  void registerScopeRange(@NotNull Editor editor, final int rangeStart, final int rangeEnd, final int tabOutOffset);

  /**
   * Registers a new scope (empty at the time of call) at the given offset. Provided offset is supposed to point at the location between
   * just inserted pair of quotes/brackets.
   */
  default void registerEmptyScope(@NotNull Editor editor, int offset) {
    registerEmptyScope(editor, offset, offset + 1);
  }

  /**
   * Same as {@link #registerEmptyScope(Editor, int)} but allows to set custom caret shift on exiting the scope. This is for the cases when scope suffix contains more than one character (e.g. a closing parenthesis and a semicolon).
   *
   * @param tabOutOffset position where caret should be moved when Tab is used to exit the scope (should be larger than {@code offset})
   */
  default void registerEmptyScope(@NotNull Editor editor, int offset, int tabOutOffset) {
    registerScopeRange(editor, offset, offset, tabOutOffset);
  }

  /**
   * Checks whether given offset is at the end of tracked scope (so if caret is located at that offset, Tab key can be used to move out of
   * the scope).
   */
  boolean hasScopeEndingAt(@NotNull Editor editor, int offset);

  int getScopeEndingAt(@NotNull Editor editor, int offset);

  /**
   * Removes a tracked scope (if any) ending at the given offset.
   *
   * @return target caret position where caret should be moved, if there was a scope ending at given offset, and -1 otherwise
   *
   * @see #hasScopeEndingAt(Editor, int)
   * @see #registerEmptyScope(Editor, int, int)
   */
  int removeScopeEndingAt(@NotNull Editor editor, int offset);
}
