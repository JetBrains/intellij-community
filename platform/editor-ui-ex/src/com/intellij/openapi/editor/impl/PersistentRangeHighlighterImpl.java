// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.util.DocumentUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implementation of the markup element for the editor and document.
 * @author max
 */
final class PersistentRangeHighlighterImpl extends RangeHighlighterImpl {
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
    // todo Denis Zhdanov
    DocumentEventImpl event = (DocumentEventImpl)e;
    final boolean shouldTranslateViaDiff = isValid() && PersistentRangeMarkerUtil.shouldTranslateViaDiff(event, getStartOffset(), getEndOffset());
    boolean wasTranslatedViaDiff = shouldTranslateViaDiff;
    if (shouldTranslateViaDiff) {
      wasTranslatedViaDiff = translatedViaDiff(e, event);
    }
    if (!wasTranslatedViaDiff) {
      super.changedUpdateImpl(e);
      if (isValid()) {
        myLine = getDocument().getLineNumber(getStartOffset());
        int endLine = getDocument().getLineNumber(getEndOffset());
        if (endLine != myLine) {
          setIntervalEnd(getDocument().getLineEndOffset(myLine));
        }
      }
    }
    if (isValid() && getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE) {
      setIntervalStart(DocumentUtil.getFirstNonSpaceCharOffset(getDocument(), myLine));
      setIntervalEnd(getDocument().getLineEndOffset(myLine));
    }
  }

  private boolean translatedViaDiff(@NotNull DocumentEvent e, @NotNull DocumentEventImpl event) {
    try {
      myLine = event.translateLineViaDiff(myLine);
    }
    catch (FilesTooBigForDiffException ignored) {
      return false;
    }
    if (myLine < 0 || myLine >= getDocument().getLineCount()) {
      invalidate(e);
    }
    else {
      DocumentEx document = getDocument();
      setIntervalStart(document.getLineStartOffset(myLine));
      setIntervalEnd(document.getLineEndOffset(myLine));
    }
    return true;
  }

  @Override
  public String toString() {
    return "PersistentRangeHighlighter" +
           (isGreedyToLeft() ? "[" : "(") +
           (isValid() ? "valid" : "invalid") + "," + getStartOffset() + "," + getEndOffset() + " - " + myLine +
           (isGreedyToRight() ? "]" : ")");
  }
}
