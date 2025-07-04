// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * Some editors may want to automatically move their carets
 * (i.e., when the caret gets into a non-expandable section of the editor).
 * However, sometimes this behavior might be undesirable: i.e., templates rely on caret positions,
 * and if the caret is unexpectedly moved, they might break.
 * <p>
 * This interface allows you to control this behavior.
 * When such an editor wants to move a caret, it should call {@link #isCaretMovementAllowed(Editor)}
 * to check if it's currently allowed.
 * <p>
 * When the platform logic needs to forbid caret movement for a given editor, it should call the action inside
 * {@link #forbidCaretMovementInsideIfNeeded(Editor, Runnable)}
 * <p>
 * Note that the controller itself should be implemented and installed via {@link #install(Editor, CaretAutoMoveController)}
 * on the client side beforehand.
 */
public interface CaretAutoMoveController {
  Key<CaretAutoMoveController> KEY = Key.create("editorCaretAutoMoveController");

  void notifyCaretMovementAllowed(boolean allowed);
  boolean isCaretMovingAllowed();

  static void forbidCaretMovementInsideIfNeeded(@NotNull Editor editor, Runnable runnable) {
    CaretAutoMoveController controller = editor.getUserData(KEY);
    if (controller != null) controller.notifyCaretMovementAllowed(false);
    try {
      runnable.run();
    } finally {
      if (controller != null) controller.notifyCaretMovementAllowed(true);
    }
  }

  static boolean isCaretMovementAllowed(@NotNull Editor editor) {
    CaretAutoMoveController controller = editor.getUserData(KEY);
    return controller == null || controller.isCaretMovingAllowed();
  }

  static void install(@NotNull Editor editor, CaretAutoMoveController adjuster) {
    editor.putUserData(KEY, adjuster);
  }
}
