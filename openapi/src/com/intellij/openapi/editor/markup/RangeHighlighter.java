/*
 * Copyright 2000-2005 JetBrains s.r.o.
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