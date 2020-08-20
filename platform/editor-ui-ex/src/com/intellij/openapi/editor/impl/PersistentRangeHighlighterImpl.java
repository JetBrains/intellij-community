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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of the markup element for the editor and document.
 * @author max
 */
class PersistentRangeHighlighterImpl extends RangeHighlighterImpl {
  private int myLine; // for PersistentRangeHighlighterImpl only
  static @NotNull PersistentRangeHighlighterImpl create(@NotNull MarkupModelImpl model,
                                                        int offset,
                                                        int layer,
                                                        @NotNull HighlighterTargetArea target,
                                                        @Nullable TextAttributesKey textAttributesKey,
                                               boolean normalizeStartOffset) {
    int line = model.getDocument().getLineNumber(offset);
    int startOffset = normalizeStartOffset ? model.getDocument().getLineStartOffset(line) : offset;
    return new PersistentRangeHighlighterImpl(model, startOffset, line, layer, target, textAttributesKey);
  }

  private PersistentRangeHighlighterImpl(@NotNull MarkupModelImpl model,
                                         int startOffset,
                                         int line,
                                         int layer,
                                         @NotNull HighlighterTargetArea target,
                                         @Nullable TextAttributesKey textAttributesKey) {
    super(model, startOffset, model.getDocument().getLineEndOffset(line), layer, target, textAttributesKey, false, false);

    myLine = line;
  }

  @Override
  public boolean isPersistent() {
    return true;
  }

  @Override
  protected void changedUpdateImpl(@NotNull DocumentEvent e) {
    myLine = persistentHighlighterUpdate(e, myLine, getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE);
  }

  @Override
  public String toString() {
    return "PersistentRangeHighlighter" +
           (isGreedyToLeft() ? "[" : "(") +
           (isValid() ? "valid" : "invalid") + "," + getStartOffset() + "," + getEndOffset() + " - " + myLine +
           (isGreedyToRight() ? "]" : ")");
  }
}
