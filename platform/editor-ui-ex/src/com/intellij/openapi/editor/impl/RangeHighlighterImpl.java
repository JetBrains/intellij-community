/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.Getter;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Implementation of the markup element for the editor and document.
 * @author max
 */
class RangeHighlighterImpl extends RangeMarkerImpl implements RangeHighlighterEx, Getter<RangeHighlighterEx> {
  private final RangeHighlighterData data;

  RangeHighlighterImpl(@NotNull MarkupModel model,
                       int start,
                       int end,
                       int layer,
                       @NotNull HighlighterTargetArea target,
                       TextAttributes textAttributes, boolean greedyToLeft, boolean greedyToRight) {
    super((DocumentEx)model.getDocument(), start, end,false);

    data = new RangeHighlighterData(model, target, textAttributes) {
      @NotNull
      @Override
      public RangeHighlighterEx getRangeHighlighter() {
        return RangeHighlighterImpl.this;
      }
    };

    registerInTree(start, end, greedyToLeft, greedyToRight, layer);
  }

  protected RangeHighlighterData getData() {
    return data;
  }

  @Override
  protected void registerInTree(int start, int end, boolean greedyToLeft, boolean greedyToRight, int layer) {
    // we store highlighters in MarkupModel
    ((MarkupModelEx)data.getMarkupModel()).addRangeHighlighter(this, start, end, greedyToLeft, greedyToRight, layer);
  }

  @Override
  protected boolean unregisterInTree() {
    if (!isValid()) return false;
    // we store highlighters in MarkupModel
    getData().getMarkupModel().removeHighlighter(this);
    return true;
  }

  @Override
  public RangeHighlighterImpl get() {
    return this;
  }

  // delegates
  @Override
  public TextAttributes getTextAttributes() {
    return getData().getTextAttributes();
  }

  @Override
  public void setTextAttributes(@NotNull TextAttributes textAttributes) {
    getData().setTextAttributes(textAttributes);
  }

  boolean changeAttributesNoEvents(@NotNull Consumer<RangeHighlighterEx> change) {
    return getData().changeAttributesInBatch(change);
  }

  @Override
  public int getLayer() {
    RangeHighlighterTree.RHNode node = (RangeHighlighterTree.RHNode)(Object)myNode;
    return node == null ? -1 : node.myLayer;
  }

  @Override
  public HighlighterTargetArea getTargetArea() {
    return getData().getTargetArea();
  }

  @Override
  public LineMarkerRenderer getLineMarkerRenderer() {
    return getData().getLineMarkerRenderer();
  }

  @Override
  public void setLineMarkerRenderer(LineMarkerRenderer renderer) {
    getData().setLineMarkerRenderer(renderer);
  }

  @Override
  public CustomHighlighterRenderer getCustomRenderer() {
    return getData().getCustomRenderer();
  }

  @Override
  public void setCustomRenderer(CustomHighlighterRenderer renderer) {
    getData().setCustomRenderer(renderer);
  }

  @Override
  public GutterIconRenderer getGutterIconRenderer() {
    return getData().getGutterIconRenderer();
  }

  @Override
  public void setGutterIconRenderer(GutterIconRenderer renderer) {
    getData().setGutterIconRenderer(renderer);
  }

  @Override
  public Color getErrorStripeMarkColor() {
    return getData().getErrorStripeMarkColor();
  }

  @Override
  public void setErrorStripeMarkColor(Color color) {
    getData().setErrorStripeMarkColor(color);
  }

  @Override
  public Object getErrorStripeTooltip() {
    return getData().getErrorStripeTooltip();
  }

  @Override
  public void setErrorStripeTooltip(Object tooltipObject) {
    getData().setErrorStripeTooltip(tooltipObject);
  }

  @Override
  public boolean isThinErrorStripeMark() {
    return getData().isThinErrorStripeMark();
  }

  @Override
  public void setThinErrorStripeMark(boolean value) {
    getData().setThinErrorStripeMark(value);
  }

  @Override
  public Color getLineSeparatorColor() {
    return getData().getLineSeparatorColor();
  }

  @Override
  public void setLineSeparatorColor(Color color) {
    getData().setLineSeparatorColor(color);
  }

  @Override
  public void setLineSeparatorRenderer(LineSeparatorRenderer renderer) {
    getData().setLineSeparatorRenderer(renderer);
  }

  @Override
  public LineSeparatorRenderer getLineSeparatorRenderer() {
    return getData().getLineSeparatorRenderer();
  }

  @Override
  public SeparatorPlacement getLineSeparatorPlacement() {
    return getData().getLineSeparatorPlacement();
  }

  @Override
  public void setLineSeparatorPlacement(@Nullable SeparatorPlacement placement) {
    getData().setLineSeparatorPlacement(placement);
  }

  @Override
  public void setEditorFilter(@NotNull MarkupEditorFilter filter) {
    getData().setEditorFilter(filter);
  }

  @Override
  @NotNull
  public MarkupEditorFilter getEditorFilter() {
    return getData().getEditorFilter();
  }

  @Override
  public boolean isAfterEndOfLine() {
    return getData().isAfterEndOfLine();
  }

  @Override
  public void setAfterEndOfLine(boolean afterEndOfLine) {
    getData().setAfterEndOfLine(afterEndOfLine);
  }

  @Override
  public int getAffectedAreaStartOffset() {
    return getData().getAffectedAreaStartOffset();
  }

  @Override
  public int getAffectedAreaEndOffset() {
    return getData().getAffectedAreaEndOffset();
  }

  @Override
  public String toString() {
    return "RangeHighlighter: ("+getStartOffset()+","+getEndOffset()+"); layer:"+getLayer()+"; tooltip: "+getData().getErrorStripeTooltip();
  }
}
