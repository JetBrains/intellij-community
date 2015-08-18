/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
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
