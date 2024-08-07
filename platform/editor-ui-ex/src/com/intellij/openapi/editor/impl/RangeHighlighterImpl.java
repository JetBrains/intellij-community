// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.BitUtil;
import com.intellij.util.Consumer;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Objects;

/**
 * Implementation of the markup element for the editor and document.
 * @author max
 */
sealed class RangeHighlighterImpl extends RangeMarkerImpl implements RangeHighlighterEx permits PersistentRangeHighlighterImpl {
  private static final Logger LOG = Logger.getInstance(RangeHighlighterImpl.class);
  @SuppressWarnings({"InspectionUsingGrayColors", "UseJBColor"})
  private static final Color NULL_COLOR = new Color(0, 0, 0); // must be a new instance to work as a sentinel
  private static final Key<Boolean> VISIBLE_IF_FOLDED = Key.create("visible.folded");

  private final MarkupModelImpl myModel;
  private TextAttributes myForcedTextAttributes;
  private TextAttributesKey myTextAttributesKey;
  private LineMarkerRenderer myLineMarkerRenderer;
  private Color myErrorStripeColor;
  private Color myLineSeparatorColor;
  private SeparatorPlacement mySeparatorPlacement;
  private GutterIconRenderer myGutterIconRenderer;
  private volatile Object myErrorStripeTooltip;
  private MarkupEditorFilter myFilter = MarkupEditorFilter.EMPTY;
  private CustomHighlighterRenderer myCustomRenderer;
  private LineSeparatorRenderer myLineSeparatorRenderer;

  @Mask
  private byte myFlags;

  private static final byte AFTER_END_OF_LINE_MASK = 1;
  private static final byte ERROR_STRIPE_IS_THIN_MASK = 1<<1;
  private static final byte TARGET_AREA_IS_EXACT_MASK = 1<<2;
  private static final byte IN_BATCH_CHANGE_MASK = 1<<3;
  static final byte CHANGED_MASK = 1<<4;
  static final byte RENDERERS_CHANGED_MASK = 1<<5;
  static final byte FONT_STYLE_CHANGED_MASK = 1<<6;
  static final byte FOREGROUND_COLOR_CHANGED_MASK = -128;

  @MagicConstant(intValues = {AFTER_END_OF_LINE_MASK, ERROR_STRIPE_IS_THIN_MASK, TARGET_AREA_IS_EXACT_MASK, IN_BATCH_CHANGE_MASK,
    CHANGED_MASK, RENDERERS_CHANGED_MASK, FONT_STYLE_CHANGED_MASK, FOREGROUND_COLOR_CHANGED_MASK})
  private @interface Flag {}

  @MagicConstant(flags = {AFTER_END_OF_LINE_MASK, ERROR_STRIPE_IS_THIN_MASK, TARGET_AREA_IS_EXACT_MASK, IN_BATCH_CHANGE_MASK,
      CHANGED_MASK, RENDERERS_CHANGED_MASK, FONT_STYLE_CHANGED_MASK, FOREGROUND_COLOR_CHANGED_MASK})
  private @interface Mask {}

  RangeHighlighterImpl(@NotNull MarkupModelImpl model,
                       int start,
                       int end,
                       int layer,
                       @NotNull HighlighterTargetArea target,
                       @Nullable TextAttributesKey textAttributesKey,
                       boolean greedyToLeft,
                       boolean greedyToRight) {
    super((DocumentEx)model.getDocument(), start, end, false, true);
    myTextAttributesKey = textAttributesKey;
    setFlag(TARGET_AREA_IS_EXACT_MASK, target == HighlighterTargetArea.EXACT_RANGE);
    myModel = model;

    registerInTree(start, end, greedyToLeft, greedyToRight, layer);
    if (LOG.isDebugEnabled()) {
      LOG.debug("RangeHighlighterImpl: create " + this +" ("+getTextRange().substring(getDocument().getText())+")");
    }
  }

  private boolean isFlagSet(@Flag byte mask) {
    return BitUtil.isSet(myFlags, mask);
  }

  // take one bit specified by mask from value and store it to myFlags; all other bits remain intact
  private void setFlag(@Flag byte mask, boolean value) {
    //noinspection MagicConstant
    myFlags = BitUtil.set(myFlags, mask, value);
  }

  // take bits specified by mask from value and store them to myFlags; all other bits remain intact
  private void setMask(@Mask int mask, @Mask int value) {
    //noinspection MagicConstant
    myFlags = (byte)(myFlags & ~mask | value);
  }

  @Override
  public TextAttributesKey getTextAttributesKey() {
    return myTextAttributesKey;
  }

  @Override
  @ApiStatus.Internal
  public @Nullable TextAttributes getForcedTextAttributes() {
    return myForcedTextAttributes;
  }

  @Override
  @ApiStatus.Internal
  public @Nullable Color getForcedErrorStripeMarkColor() {
    return myErrorStripeColor;
  }

  @Override
  public @Nullable TextAttributes getTextAttributes(@Nullable("when null, the global scheme will be used") EditorColorsScheme scheme) {
    if (myForcedTextAttributes != null) return myForcedTextAttributes;
    if (myTextAttributesKey == null) return null;

    EditorColorsScheme colorScheme = scheme == null ? EditorColorsManager.getInstance().getGlobalScheme() : scheme;
    return colorScheme.getAttributes(myTextAttributesKey);
  }

  @Override
  public void setTextAttributes(@Nullable TextAttributes textAttributes) {
    TextAttributes old = myForcedTextAttributes;
    if (old == textAttributes) return;

    myForcedTextAttributes = textAttributes;

    if (old == TextAttributes.ERASE_MARKER || textAttributes == TextAttributes.ERASE_MARKER ||
        old == null && myTextAttributesKey != null) {
      fireChanged(false, true, true);
    }
    else if (!Objects.equals(old, textAttributes)) {
      fireChanged(false, getFontStyle(old) != getFontStyle(textAttributes),
                  !Objects.equals(getForegroundColor(old), getForegroundColor(textAttributes)));
    }
  }

  @Override
  public void setTextAttributesKey(@NotNull TextAttributesKey textAttributesKey) {
    TextAttributesKey old = myTextAttributesKey;
    myTextAttributesKey = textAttributesKey;
    if (!textAttributesKey.equals(old)) {
      fireChanged(false, myForcedTextAttributes == null, myForcedTextAttributes == null);
    }
  }

  @Override
  public void setVisibleIfFolded(boolean value) {
    putUserData(VISIBLE_IF_FOLDED, value ? Boolean.TRUE : null);
  }

  @Override
  public boolean isVisibleIfFolded() {
    return VISIBLE_IF_FOLDED.isIn(this);
  }

  private static int getFontStyle(@Nullable TextAttributes textAttributes) {
    return textAttributes == null ? Font.PLAIN : textAttributes.getFontType();
  }

  private static Color getForegroundColor(TextAttributes textAttributes) {
    return textAttributes == null ? null : textAttributes.getForegroundColor();
  }

  @Override
  public @NotNull HighlighterTargetArea getTargetArea() {
    return isFlagSet(TARGET_AREA_IS_EXACT_MASK) ? HighlighterTargetArea.EXACT_RANGE : HighlighterTargetArea.LINES_IN_RANGE;
  }

  @Override
  public LineMarkerRenderer getLineMarkerRenderer() {
    return myLineMarkerRenderer;
  }

  @Override
  public void setLineMarkerRenderer(LineMarkerRenderer renderer) {
    boolean oldRenderedInGutter = isRenderedInGutter();
    LineMarkerRenderer old = myLineMarkerRenderer;
    myLineMarkerRenderer = renderer;
    if (isRenderedInGutter() != oldRenderedInGutter) {
      myModel.treeFor(this).updateRenderedFlags(this);
    }
    if (!Objects.equals(old, renderer)) {
      fireChanged(true, false, false);
    }
  }

  @Override
  public CustomHighlighterRenderer getCustomRenderer() {
    return myCustomRenderer;
  }

  @Override
  public void setCustomRenderer(CustomHighlighterRenderer renderer) {
    CustomHighlighterRenderer old = myCustomRenderer;
    myCustomRenderer = renderer;
    if (!Objects.equals(old, renderer)) {
      fireChanged(true, false, false);
    }
  }

  @Override
  public GutterIconRenderer getGutterIconRenderer() {
    return myGutterIconRenderer;
  }

  @Override
  public void setGutterIconRenderer(GutterIconRenderer renderer) {
    boolean oldRenderedInGutter = isRenderedInGutter();
    GutterMark old = myGutterIconRenderer;
    myGutterIconRenderer = renderer;
    if (isRenderedInGutter() != oldRenderedInGutter) {
      myModel.treeFor(this).updateRenderedFlags(this);
    }
    if (!Objects.equals(old, renderer)) {
      fireChanged(true, false, false);
      if (old instanceof Disposable oldDisposableRenderer) {
        Disposer.dispose(oldDisposableRenderer);
      }
    }
  }

  @Override
  public Color getErrorStripeMarkColor(@Nullable("when null, the global scheme will be used") EditorColorsScheme scheme) {
    if (myErrorStripeColor == NULL_COLOR) return null;
    if (myErrorStripeColor != null) return myErrorStripeColor;
    if (myForcedTextAttributes != null) return myForcedTextAttributes.getErrorStripeColor();
    TextAttributes textAttributes = getTextAttributes(scheme);
    return textAttributes != null ? textAttributes.getErrorStripeColor() : null;
  }

  @Override
  public void setErrorStripeMarkColor(@Nullable Color color) {
    if (color == null) color = NULL_COLOR;
    Color old = myErrorStripeColor;
    myErrorStripeColor = color;
    if (!Objects.equals(old, color)) {
      fireChanged(false, false, false);
    }
  }

  @Override
  public Object getErrorStripeTooltip() {
    return myErrorStripeTooltip;
  }

  @Override
  public void setErrorStripeTooltip(Object tooltipObject) {
    Object old = myErrorStripeTooltip;
    myErrorStripeTooltip = tooltipObject;
    if (!Objects.equals(old, tooltipObject)) {
      fireChanged(false, false, false);
    }
  }

  @Override
  public boolean isThinErrorStripeMark() {
    return isFlagSet(ERROR_STRIPE_IS_THIN_MASK);
  }

  @Override
  public void setThinErrorStripeMark(boolean value) {
    boolean old = isThinErrorStripeMark();
    setFlag(ERROR_STRIPE_IS_THIN_MASK, value);
    if (old != value) {
      fireChanged(false, false, false);
    }
  }

  @Override
  public Color getLineSeparatorColor() {
    return myLineSeparatorColor;
  }

  @Override
  public void setLineSeparatorColor(Color color) {
    Color old = myLineSeparatorColor;
    myLineSeparatorColor = color;
    if (!Objects.equals(old, color)) {
      fireChanged(false, false, false);
    }
  }

  @Override
  public SeparatorPlacement getLineSeparatorPlacement() {
    return mySeparatorPlacement;
  }

  @Override
  public void setLineSeparatorPlacement(@Nullable SeparatorPlacement placement) {
    SeparatorPlacement old = mySeparatorPlacement;
    mySeparatorPlacement = placement;
    if (!Objects.equals(old, placement)) {
      fireChanged(false, false, false);
    }
  }

  @Override
  public void setEditorFilter(@NotNull MarkupEditorFilter filter) {
    myFilter = filter;
    fireChanged(false, false, false);
  }

  @Override
  public @NotNull MarkupEditorFilter getEditorFilter() {
    return myFilter;
  }

  @Override
  public boolean isAfterEndOfLine() {
    return isFlagSet(AFTER_END_OF_LINE_MASK);
  }

  @Override
  public void setAfterEndOfLine(boolean afterEndOfLine) {
    boolean old = isAfterEndOfLine();
    setFlag(AFTER_END_OF_LINE_MASK, afterEndOfLine);
    if (old != afterEndOfLine) {
      fireChanged(false, false, false);
    }
  }

  @Override
  public void setGreedyToLeft(boolean greedy) {
    boolean old = isGreedyToLeft();
    super.setGreedyToLeft(greedy);
    if (old != greedy) {
      fireChanged(false, false, false);
    }
  }

  @Override
  public void setGreedyToRight(boolean greedy) {
    boolean old = isGreedyToRight();
    super.setGreedyToRight(greedy);
    if (old != greedy) {
      fireChanged(false, false, false);
    }
  }

  @Override
  public void setStickingToRight(boolean value) {
    boolean old = isStickingToRight();
    super.setStickingToRight(value);
    if (old != value) {
      fireChanged(false, false, false);
    }
  }

  private void fireChanged(boolean renderersChanged, boolean fontStyleChanged, boolean foregroundColorChanged) {
    if (isFlagSet(IN_BATCH_CHANGE_MASK)) {
      // under IN_BATCH_CHANGE_MASK, do not fire events, just add flags above
      int changedFlags = CHANGED_MASK|RENDERERS_CHANGED_MASK|FONT_STYLE_CHANGED_MASK|FOREGROUND_COLOR_CHANGED_MASK;
      int value = CHANGED_MASK
        | (renderersChanged ? RENDERERS_CHANGED_MASK : 0)
        | (fontStyleChanged ? FONT_STYLE_CHANGED_MASK : 0)
        | (foregroundColorChanged ? FOREGROUND_COLOR_CHANGED_MASK : 0);
      setMask(changedFlags, value | myFlags);
    }
    else {
      myModel.fireAttributesChanged(this, renderersChanged, fontStyleChanged, foregroundColorChanged);
    }
  }

  @Override
  public int getAffectedAreaStartOffset() {
    int startOffset = getStartOffset();
    return switch (getTargetArea()) {
      case EXACT_RANGE -> startOffset;
      case LINES_IN_RANGE -> {
        Document document = myModel.getDocument();
        int textLength = document.getTextLength();
        if (startOffset >= textLength) yield textLength;
        yield document.getLineStartOffset(document.getLineNumber(startOffset));
      }
    };
  }

  @Override
  public int getAffectedAreaEndOffset() {
    int endOffset = getEndOffset();
    return switch (getTargetArea()) {
      case EXACT_RANGE -> endOffset;
      case LINES_IN_RANGE -> {
        Document document = myModel.getDocument();
        int textLength = document.getTextLength();
        if (endOffset >= textLength) yield endOffset;
        yield Math.min(textLength, document.getLineEndOffset(document.getLineNumber(endOffset)) + 1);
      }
    };
  }

  // synchronized to avoid concurrent changes
  @Mask
  synchronized byte changeAttributesNoEvents(@NotNull Consumer<? super RangeHighlighterEx> change) {
    assert !isFlagSet(IN_BATCH_CHANGE_MASK);
    assert !isFlagSet(CHANGED_MASK);
    setMask(IN_BATCH_CHANGE_MASK | RENDERERS_CHANGED_MASK | FONT_STYLE_CHANGED_MASK | FOREGROUND_COLOR_CHANGED_MASK, IN_BATCH_CHANGE_MASK);
    byte result;
    try {
      change.consume(this);
    }
    finally {
      result = myFlags;
      setMask(IN_BATCH_CHANGE_MASK|CHANGED_MASK|RENDERERS_CHANGED_MASK|FONT_STYLE_CHANGED_MASK|FOREGROUND_COLOR_CHANGED_MASK,0);
    }
    return result;
  }

  private @NotNull MarkupModel getMarkupModel() {
    return myModel;
  }

  @Override
  public void setLineSeparatorRenderer(LineSeparatorRenderer renderer) {
    LineSeparatorRenderer old = myLineSeparatorRenderer;
    myLineSeparatorRenderer = renderer;
    if (!Objects.equals(old, renderer)) {
      fireChanged(true, false, false);
    }
  }

  @Override
  public LineSeparatorRenderer getLineSeparatorRenderer() {
    return myLineSeparatorRenderer;
  }

  @Override
  protected void registerInTree(int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    // we store highlighters in MarkupModel
    myModel.addRangeHighlighter(this, start, end, greedyToLeft, greedyToRight, layer);
  }

  @Override
  protected void unregisterInTree() {
    // we store highlighters in MarkupModel
    getMarkupModel().removeHighlighter(this);
  }

  @Override
  public void dispose() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("RangeHighlighterImpl: dispose " + this);
    }
    super.dispose();
    GutterIconRenderer renderer = getGutterIconRenderer();
    if (renderer instanceof Disposable disposableRenderer) {
      Disposer.dispose(disposableRenderer);
    }
  }

  @Override
  public int getLayer() {
    RangeHighlighterTree.RHNode node = (RangeHighlighterTree.RHNode)(Object)myNode;
    return node == null ? -1 : node.myLayer;
  }

  @Override
  public boolean isRenderedInGutter() {
    RangeHighlighterTree.RHNode node = (RangeHighlighterTree.RHNode)(Object)myNode;
    return node != null && node.isRenderedInGutter() || RangeHighlighterEx.super.isRenderedInGutter();
  }

  @Override
  public @NonNls String toString() {
    return "RangeHighlighter: " +
           (isValid() ? "" : "(invalid)") +
           "("+getStartOffset()+","+getEndOffset()+"); layer:"+getLayer()+"; tooltip: "+getErrorStripeTooltip();
  }
}
