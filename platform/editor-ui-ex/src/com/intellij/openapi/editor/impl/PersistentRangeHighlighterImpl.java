/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.DocumentUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of the markup element for the editor and document.
 * @author max
 */
class PersistentRangeHighlighterImpl extends RangeHighlighterImpl implements RangeHighlighterEx {
  static PersistentRangeHighlighterImpl create(@NotNull MarkupModel model,
                                               int offset,
                                               int layer,
                                               @NotNull HighlighterTargetArea target,
                                               @Nullable TextAttributes textAttributes,
                                               boolean normalizeStartOffset) {
    int line = model.getDocument().getLineNumber(offset);
    int startOffset = normalizeStartOffset ? model.getDocument().getLineStartOffset(line) : offset;
    return new PersistentRangeHighlighterImpl(model, startOffset, line, layer, target, textAttributes);
  }

  private PersistentRangeHighlighterImpl(@NotNull MarkupModel model,
                                         int startOffset,
                                         int line,
                                         int layer,
                                         @NotNull HighlighterTargetArea target,
                                         @Nullable TextAttributes textAttributes) {
    super(model, startOffset, model.getDocument().getLineEndOffset(line), layer, target, textAttributes, false, false);

    setLine(line);
  }

  @Override
  protected void changedUpdateImpl(DocumentEvent e) {
    // todo Denis Zhdanov
    DocumentEventImpl event = (DocumentEventImpl)e;
    final boolean shouldTranslateViaDiff = PersistentRangeMarkerUtil.shouldTranslateViaDiff(event, this);
    boolean wasTranslatedViaDiff = shouldTranslateViaDiff;
    if (shouldTranslateViaDiff) {
      wasTranslatedViaDiff = translatedViaDiff(e, event);
    }
    if (!wasTranslatedViaDiff) {
      super.changedUpdateImpl(e);
      if (isValid()) {
        setLine(getDocument().getLineNumber(getStartOffset()));
        int endLine = getDocument().getLineNumber(getEndOffset());
        if (endLine != getLine()) {
          setIntervalEnd(getDocument().getLineEndOffset(getLine()));
        }
      }
    }
    if (isValid() && getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE) {
      setIntervalStart(DocumentUtil.getFirstNonSpaceCharOffset(getDocument(), getLine()));
      setIntervalEnd(getDocument().getLineEndOffset(getLine()));
    }
  }

  private boolean translatedViaDiff(DocumentEvent e, DocumentEventImpl event) {
    try {
      setLine(event.translateLineViaDiff(getLine()));
    }
    catch (FilesTooBigForDiffException ignored) {
      return false;
    }
    if (getLine() < 0 || getLine() >= getDocument().getLineCount()) {
      invalidate(e);
    }
    else {
      DocumentEx document = getDocument();
      setIntervalStart(document.getLineStartOffset(getLine()));
      setIntervalEnd(document.getLineEndOffset(getLine()));
    }
    return true;
  }

  @Override
  public String toString() {
    return "PersistentRangeHighlighter" +
           (isGreedyToLeft() ? "[" : "(") +
           (isValid() ? "valid" : "invalid") + "," + getStartOffset() + "," + getEndOffset() + " - " + getLine() +
           (isGreedyToRight() ? "]" : ")");
  }

  // delegates
  private int getLine() {
    return getData().myLine;
  }

  private void setLine(int line) {
    getData().myLine = line;
  }
}
