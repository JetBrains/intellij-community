/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.editor.RangeMarker;

import java.awt.*;

public interface RangeHighlighter extends RangeMarker {
  int getLayer();

  HighlighterTargetArea getTargetArea();

  TextAttributes getTextAttributes();

  LineMarkerRenderer getLineMarkerRenderer();

  void setLineMarkerRenderer(LineMarkerRenderer renderer);

  GutterIconRenderer getGutterIconRenderer();

  void setGutterIconRenderer(GutterIconRenderer renderer);

  Color getErrorStripeMarkColor();

  void setErrorStripeMarkColor(Color color);

  Object getErrorStripeTooltip();

  void setErrorStripeTooltip(Object tooltipObject);

  boolean isThinErrorStripeMark();

  void setThinErrorStripeMark(boolean value);

  Color getLineSeparatorColor();

  void setLineSeparatorColor(Color color);

  SeparatorPlacement getLineSeparatorPlacement();

  void setLineSeparatorPlacement(SeparatorPlacement placement);

  void setEditorFilter(MarkupEditorFilter filter);

  MarkupEditorFilter getEditorFilter();
}