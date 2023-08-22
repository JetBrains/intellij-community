// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

import java.util.EventObject;

public abstract class DocumentEvent extends EventObject {
  protected DocumentEvent(@NotNull Document document) {
    super(document);
  }

  public @NotNull Document getDocument() {
    return (Document)getSource();
  }

  /**
   * The start offset of a text change.
   */
  public abstract int getOffset();

  /**
   * In case of any document modifications other than moving text, this method returns the same as {@link #getOffset()}.
   *
   * Moving a text fragment using {@link com.intellij.openapi.editor.ex.DocumentEx#moveText} is accomplished by a combination of
   * insertion and deletion. For these two events this method returns the offset of the complementary deletion/insertion event: <ul>
   *   <li/> On insertion: returns the source offset, that is, the offset of the original text fragment, which will be removed.
   *   <li/> On deletion: returns the destination offset, that is, the offset of the newly inserted text fragment.
   * </ul>
   * Note that the result of this method points to a relevant fragment of the document content only as long as
   * both the source and the destination copies of the fragment are still there, that is, only:
   *   a) during {@link DocumentListener#documentChanged} of the insertion event, and
   *   b) during {@link DocumentListener#beforeDocumentChange} of the deletion event.
   *
   * In order to obtain an adjusted value before the new fragment is inserted or after deleting the original one, consider using
   * the {@link com.intellij.util.DocumentEventUtil#getMoveOffsetBeforeInsertion}
   * and the {@link com.intellij.util.DocumentEventUtil#getMoveOffsetAfterDeletion} methods, accordingly.
   *
   * <p/> Examples:
   *
   * Moving text to the left:
   * <pre>
   *   document.moveText(2, 6, 0);  //  aa^^^^bb  ->  ^^^^aa^^^^bb  ->  ^^^^aabb
   * </pre>
   * In this example the following listener calls are performed:
   * <pre>
   *   beforeDocumentChange(insert):  aa^^^^bb      [ offset: 0, oldLength: 0, newLength: 4, moveOffset: 6, moveOffsetBeforeInsertion: 2 ]
   *        documentChanged(insert):  ^^^^aa^^^^bb  [ offset: 0, oldLength: 0, newLength: 4, moveOffset: 6 ] // moveOffset == delete.offset
   *   beforeDocumentChange(delete):  ^^^^aa^^^^bb  [ offset: 6, oldLength: 4, newLength: 0, moveOffset: 0 ] // moveOffset == insert.offset
   *        documentChanged(delete):  ^^^^aabb      [ offset: 6, oldLength: 4, newLength: 0, moveOffset: 0, moveOffsetAfterDeletion: 0 ]
   * </pre>
   *
   * Moving text to the right:
   * <pre>
   *   document.moveText(2, 6, 8);  //  aa^^^^bb  ->  aa^^^^bb^^^^  ->  aabb^^^^
   * </pre>
   * In this example the following listener calls are performed:
   * <pre>
   *   beforeDocumentChange(insert):  aa^^^^bb      [ offset: 8, oldLength: 0, newLength: 4, moveOffset: 2, moveOffsetBeforeInsertion: 2 ]
   *        documentChanged(insert):  aa^^^^bb^^^^  [ offset: 8, oldLength: 0, newLength: 4, moveOffset: 2 ] // moveOffset == delete.offset
   *   beforeDocumentChange(delete):  aa^^^^bb^^^^  [ offset: 2, oldLength: 4, newLength: 0, moveOffset: 8 ] // moveOffset == insert.offset
   *        documentChanged(delete):  aabb^^^^      [ offset: 2, oldLength: 4, newLength: 0, moveOffset: 8, moveOffsetAfterDeletion: 4 ]
   * </pre>
   */
  public int getMoveOffset() {
    return getOffset();
  }

  public abstract int getOldLength();
  public abstract int getNewLength();

  public abstract @NotNull CharSequence getOldFragment();

  public abstract @NotNull CharSequence getNewFragment();

  public abstract long getOldTimeStamp();

  public boolean isWholeTextReplaced() {
    return getOffset() == 0 && getNewLength() == getDocument().getTextLength();
  }
}
