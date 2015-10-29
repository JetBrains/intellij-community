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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.event.DocumentEvent;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 12/27/10 4:26 PM
 */
class PersistentRangeMarkerUtil {
  /**
   * Answers if document region identified by the given range marker should be translated via diff algorithm on document change
   * identified by the given event.
   *
   * @param e             event that describes document change
   * @param rangeStart    target range marker start, for which update strategy should be selected
   * @param rangeEnd      target range marker end
   * @return              <code>true</code> if target document range referenced by the given range marker should be translated via
   *                      diff algorithm; <code>false</code> otherwise
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

    // Perform complex processing only if significant document part is updated.
    return (Math.max(e.getNewLength(), e.getOldLength()) * 1.0) / e.getDocument().getTextLength() >= 0.8;
  }
}
