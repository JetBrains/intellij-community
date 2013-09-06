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

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.Consumer;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * User: cdr
 */
abstract class RangeHighlighterData {
  private final MarkupModel myModel;
  private TextAttributes myTextAttributes;
  private LineMarkerRenderer myLineMarkerRenderer;
  private Color myErrorStripeColor;
  private Color myLineSeparatorColor;
  private SeparatorPlacement mySeparatorPlacement;
  private GutterIconRenderer myGutterIconRenderer;
  private Object myErrorStripeTooltip;
  private MarkupEditorFilter myFilter = MarkupEditorFilter.EMPTY;
  private CustomHighlighterRenderer myCustomRenderer;
  int myLine; // for PersistentRangeHighlighterImpl only
  private LineSeparatorRenderer myLineSeparatorRenderer;

  private byte myFlags;

  RangeHighlighterData(@NotNull MarkupModel model,
                       @NotNull HighlighterTargetArea target,
                       TextAttributes textAttributes) {
    myTextAttributes = textAttributes;
    setFlag(TARGET_AREA_IS_EXACT_FLAG, target == HighlighterTargetArea.EXACT_RANGE);
    myModel = model;
    if (textAttributes != null) {
      myErrorStripeColor = textAttributes.getErrorStripeColor();
    }
  }

  private static final int AFTER_END_OF_LINE_FLAG = 0;
  private static final int ERROR_STRIPE_IS_THIN_FLAG = 1;
  private static final int TARGET_AREA_IS_EXACT_FLAG = 2;
  private static final int IN_BATCH_CHANGE_FLAG = 3;
  private static final int CHANGED_FLAG = 4;
  @MagicConstant(intValues = {AFTER_END_OF_LINE_FLAG, ERROR_STRIPE_IS_THIN_FLAG, TARGET_AREA_IS_EXACT_FLAG, IN_BATCH_CHANGE_FLAG, CHANGED_FLAG})
  @interface FlagConstant {}

  private boolean isFlagSet(@FlagConstant int flag) {
    int state = myFlags >> flag;
    return (state & 1) != 0;
  }

  private void setFlag(@FlagConstant int flag, boolean value) {
    assert flag < 8;
    int state = value ? 1 : 0;
    myFlags = (byte)(myFlags & ~(1 << flag) | state << flag);
  }


  @NotNull
  public abstract RangeHighlighterEx getRangeHighlighter();

  public TextAttributes getTextAttributes() {
    return myTextAttributes;
  }

  public void setTextAttributes(TextAttributes textAttributes) {
    TextAttributes old = myTextAttributes;
    myTextAttributes = textAttributes;
    if (!Comparing.equal(old, textAttributes)) {
      fireChanged();
    }
  }

  @NotNull
  public HighlighterTargetArea getTargetArea() {
    return isFlagSet(TARGET_AREA_IS_EXACT_FLAG) ? HighlighterTargetArea.EXACT_RANGE : HighlighterTargetArea.LINES_IN_RANGE;
  }

  public LineMarkerRenderer getLineMarkerRenderer() {
    return myLineMarkerRenderer;
  }

  public void setLineMarkerRenderer(LineMarkerRenderer renderer) {
    myLineMarkerRenderer = renderer;
    fireChanged();
  }

  public CustomHighlighterRenderer getCustomRenderer() {
    return myCustomRenderer;
  }

  public void setCustomRenderer(CustomHighlighterRenderer renderer) {
    myCustomRenderer = renderer;
  }

  public GutterIconRenderer getGutterIconRenderer() {
    return myGutterIconRenderer;
  }

  public void setGutterIconRenderer(GutterIconRenderer renderer) {
    GutterMark old = myGutterIconRenderer;
    myGutterIconRenderer = renderer;
    if (!Comparing.equal(old, renderer)) {
      fireChanged();
    }
  }

  public Color getErrorStripeMarkColor() {
    return myErrorStripeColor;
  }

  public void setErrorStripeMarkColor(Color color) {
    Color old = myErrorStripeColor;
    myErrorStripeColor = color;
    if (!Comparing.equal(old, color)) {
      fireChanged();
    }
  }

  public Object getErrorStripeTooltip() {
    return myErrorStripeTooltip;
  }

  public void setErrorStripeTooltip(Object tooltipObject) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Object old = myErrorStripeTooltip;
    myErrorStripeTooltip = tooltipObject;
    if (!Comparing.equal(old, tooltipObject)) {
      fireChanged();
    }
  }

  public boolean isThinErrorStripeMark() {
    return isFlagSet(ERROR_STRIPE_IS_THIN_FLAG);
  }

  public void setThinErrorStripeMark(boolean value) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    boolean old = isThinErrorStripeMark();
    setFlag(ERROR_STRIPE_IS_THIN_FLAG, value);
    if (old != value) {
      fireChanged();
    }
  }

  public Color getLineSeparatorColor() {
    return myLineSeparatorColor;
  }

  public void setLineSeparatorColor(Color color) {
    Color old = myLineSeparatorColor;
    myLineSeparatorColor = color;
    if (!Comparing.equal(old, color)) {
      fireChanged();
    }
  }

  public SeparatorPlacement getLineSeparatorPlacement() {
    return mySeparatorPlacement;
  }

  public void setLineSeparatorPlacement(@Nullable SeparatorPlacement placement) {
    SeparatorPlacement old = mySeparatorPlacement;
    mySeparatorPlacement = placement;
    if (!Comparing.equal(old, placement)) {
      fireChanged();
    }
  }

  public void setEditorFilter(@NotNull MarkupEditorFilter filter) {
    myFilter = filter;
    fireChanged();
  }

  @NotNull
  public MarkupEditorFilter getEditorFilter() {
    return myFilter;
  }

  public boolean isAfterEndOfLine() {
    return isFlagSet(AFTER_END_OF_LINE_FLAG);
  }

  public void setAfterEndOfLine(boolean afterEndOfLine) {
    boolean old = isAfterEndOfLine();
    setFlag(AFTER_END_OF_LINE_FLAG, afterEndOfLine);
    if (old != afterEndOfLine) {
      fireChanged();
    }
  }

  private void fireChanged() {
    if (myModel instanceof MarkupModelEx) {
      if (isFlagSet(IN_BATCH_CHANGE_FLAG)) {
        setFlag(CHANGED_FLAG, true);
      }
      else {
        ((MarkupModelEx)myModel).fireAttributesChanged(getRangeHighlighter());
      }
    }
  }

  public int getAffectedAreaStartOffset() {
    int startOffset = getRangeHighlighter().getStartOffset();
    if (getTargetArea() == HighlighterTargetArea.EXACT_RANGE) return startOffset;
    Document document = myModel.getDocument();
    int textLength = document.getTextLength();
    if (startOffset >= textLength) return textLength;
    return document.getLineStartOffset(document.getLineNumber(startOffset));
  }

  public int getAffectedAreaEndOffset() {
    int endOffset = getRangeHighlighter().getEndOffset();
    if (getTargetArea() == HighlighterTargetArea.EXACT_RANGE) return endOffset;
    Document document = myModel.getDocument();
    int textLength = document.getTextLength();
    if (endOffset >= textLength) return endOffset;
    return Math.min(textLength, document.getLineEndOffset(document.getLineNumber(endOffset)) + 1);
  }

  // returns true if change was detected
  boolean changeAttributesInBatch(@NotNull Consumer<RangeHighlighterEx> change) {
    assert !isFlagSet(IN_BATCH_CHANGE_FLAG);
    assert !isFlagSet(CHANGED_FLAG);
    setFlag(IN_BATCH_CHANGE_FLAG, true);
    boolean result;
    try {
      change.consume(getRangeHighlighter());
    }
    finally {
      setFlag(IN_BATCH_CHANGE_FLAG, false);
      result = isFlagSet(CHANGED_FLAG);
      setFlag(CHANGED_FLAG, false);
    }
    return result;
  }

  public MarkupModel getMarkupModel() {
    return myModel;
  }

  public void setLineSeparatorRenderer(LineSeparatorRenderer renderer) {
    myLineSeparatorRenderer = renderer;
  }

  public LineSeparatorRenderer getLineSeparatorRenderer() {
    return myLineSeparatorRenderer;
  }
}
