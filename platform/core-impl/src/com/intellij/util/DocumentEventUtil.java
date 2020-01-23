// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import org.jetbrains.annotations.NotNull;

public final class DocumentEventUtil {
  private DocumentEventUtil() {
  }

  /**
   * Moving a text fragment using {@link DocumentEx#moveText} is accomplished by a combination of insertion and deletion.
   * This method tells whether the event notifies about an insertion that, together with a subsequent deletion, makes up the text movement.
   *
   * A move insertion event is also the one during which all range markers contained in the text fragment being moved are evacuated to
   * their destination.
   */
  public static boolean isMoveInsertion(@NotNull DocumentEvent e) {
    return e.getOldLength() == 0 && e.getMoveOffset() != e.getOffset();
  }

  /**
   * Tells whether the event notifies about a deletion that, together with a preceding insertion, makes up a text movement.
   * @see DocumentEventUtil#isMoveInsertion
   */
  public static boolean isMoveDeletion(@NotNull DocumentEvent e) {
    return e.getNewLength() == 0 && e.getMoveOffset() != e.getOffset();
  }

  /**
   * This method should be used instead of {@link DocumentEvent#getMoveOffset()} to obtain a relevant move offset
   * corresponding to the document content when calling from the {@link DocumentListener#beforeDocumentChange} listener method
   * notifying about a {@link #isMoveInsertion move insertion}.
   */
  public static int getMoveOffsetBeforeInsertion(@NotNull DocumentEvent e) {
    final int moveOffset = e.getMoveOffset();
    if (moveOffset > e.getOffset()) {
      return moveOffset - e.getNewLength();
    }
    return moveOffset;
  }

  /**
   * This method should be used instead of {@link DocumentEvent#getMoveOffset()} to obtain a relevant move offset
   * corresponding to the document content when calling from the {@link DocumentListener#documentChanged} listener method
   * notifying about a {@link #isMoveDeletion move deletion}.
   */
  public static int getMoveOffsetAfterDeletion(@NotNull DocumentEvent e) {
    final int moveOffset = e.getMoveOffset();
    if (moveOffset > e.getOffset()) {
      return moveOffset - e.getOldLength();
    }
    return moveOffset;
  }
}
