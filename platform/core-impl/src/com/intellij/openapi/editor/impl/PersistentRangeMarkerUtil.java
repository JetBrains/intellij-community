// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.event.DocumentEvent;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 */
final class PersistentRangeMarkerUtil {
  /**
   * Answers if document region identified by the given range marker should be translated via diff algorithm on document change
   * identified by the given event.
   *
   * @param e             event that describes document change
   * @param rangeStart    target range marker start, for which update strategy should be selected
   * @param rangeEnd      target range marker end
   * @return              {@code true} if target document range referenced by the given range marker should be translated via
   *                      diff algorithm; {@code false} otherwise
   */
  static boolean shouldTranslateViaDiff(@NotNull DocumentEvent e, int rangeStart, int rangeEnd) {
    if (e.isWholeTextReplaced()) {
      // Perform translation if the whole text is replaced.
      return true;
    }

    if (e.getOffset() >= rangeEnd || e.getOffset() + e.getOldLength() <= rangeStart) {
      // Don't perform complex processing if the change doesn't affect target range.
      return false;
    }

    // Perform complex processing only if at least 80% of document was updated.
    return Math.max(e.getNewLength(), e.getOldLength()) * 5 >= e.getDocument().getTextLength() * 4;
  }
}
