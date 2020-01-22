// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.event;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

import java.util.EventObject;

public abstract class DocumentEvent extends EventObject {
  protected DocumentEvent(@NotNull Document document) {
    super(document);
  }

  @NotNull
  public Document getDocument() {
    return (Document)getSource();
  }

  /**
   * The start offset of a text change.
   */
  public abstract int getOffset();

  /**
   * Moving a text fragment using `DocumentEx.moveText` is accomplished by a combination of insertion and deletion.
   * For these two events this method returns the offset of the complementary deletion/insertion event: <ul>
   *   <li/> On insertion: returns the source offset, that is, the offset of the original text fragment, which will be removed.
   *   <li/> On deletion: returns the destination offset, that is, the offset of the newly inserted text fragment.
   * </ul>
   * In case of any document modifications other than moving text, this method returns the same as {@link #getOffset()}.
   */
  public int getMoveOffset() {
    return getOffset();
  }

  public abstract int getOldLength();
  public abstract int getNewLength();

  @NotNull
  public abstract CharSequence getOldFragment();

  @NotNull
  public abstract CharSequence getNewFragment();

  public abstract long getOldTimeStamp();

  public boolean isWholeTextReplaced() {
    return getOffset() == 0 && getNewLength() == getDocument().getTextLength();
  }
}
