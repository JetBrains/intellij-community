// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 * @deprecated {@link DocumentEventImpl} now includes information about text movement.
 */
@Deprecated
public class RetargetRangeMarkers extends DocumentEventImpl {
  private final int myStartOffset;
  private final int myEndOffset;
  private final int myMoveDestinationOffset;

  public RetargetRangeMarkers(@NotNull Document document,
                              int startOffset, int endOffset, int moveDestinationOffset) {
    super(document, startOffset, "", "", 0, false);
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myMoveDestinationOffset = moveDestinationOffset;
  }

  public int getStartOffset() {
    return myStartOffset;
  }

  public int getEndOffset() {
    return myEndOffset;
  }

  public int getMoveDestinationOffset() {
    return myMoveDestinationOffset;
  }
}
