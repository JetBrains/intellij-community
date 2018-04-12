// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This service keeps track of scopes inside quotes or brackets in editor, which user can exit (moving caret just to the right of closing
 * quote/bracket) using Tab key. The scope is usually created (registered) when a pair of quotes/brackets is inserted into a document, and
 * lives until user 'tabs out' of this scope, or performs editing outside it.
 */
@ApiStatus.Experimental
public interface TabOutScopesTracker {
  static TabOutScopesTracker getInstance() {
    return ServiceManager.getService(TabOutScopesTracker.class);
  }

  /**
   * Registers a new scope (empty at the time of call) at the given offset. Provided offset is supposed to point at the location between
   * just inserted pair of quotes/brackets.
   */
  void registerEmptyScope(@NotNull Editor editor, int offset);

  /**
   * Checks whether given offset is at the end of tracked scope (so if caret is located at that offset, Tab key can be used to move out of
   * the scope).
   *
   * @param removeScope if {@code true}, corresponding scope (if any) will be removed after the call
   */
  boolean hasScopeEndingAt(@NotNull Editor editor, int offset, boolean removeScope);
}
