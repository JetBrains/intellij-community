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
package com.intellij.ui.components;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.RegionPainter;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * @author Sergey.Malenkov
 */
final class DefaultScrollBarUI extends AbstractScrollBarUI {
  @Override
  int getThickness() {
    return scale(isOpaque() ? 13 : 14);
  }

  @Override
  int getMinimalThickness() {
    return scale(10);
  }

  @Override
  boolean isAbsolutePositioning(MouseEvent event) {
    return SwingUtilities.isMiddleMouseButton(event);
  }

  @Override
  boolean isBorderNeeded(JComponent c) {
    return c.isOpaque() && Registry.is("ide.scroll.track.border.paint");
  }

  @Override
  void onTrackHover(boolean hover) {
    myTrackAnimator.start(hover);
  }

  @Override
  void onThumbHover(boolean hover) {
    myThumbAnimator.start(hover);
  }

  @Override
  void paintTrack(Graphics2D g, int x, int y, int width, int height, JComponent c) {
    RegionPainter<Float> p = isDark(c) ? JBScrollPane.TRACK_DARK_PAINTER : JBScrollPane.TRACK_PAINTER;
    paint(p, g, x, y, width, height, c, myTrackAnimator.myValue, false);
  }

  @Override
  void paintThumb(Graphics2D g, int x, int y, int width, int height, JComponent c) {
    RegionPainter<Float> p = isDark(c) ? JBScrollPane.THUMB_DARK_PAINTER : JBScrollPane.THUMB_PAINTER;
    paint(p, g, x, y, width, height, c, myThumbAnimator.myValue, Registry.is("ide.scroll.thumb.small.if.opaque"));
  }

  int getTrackOffset(int offset) {
    return Registry.is("ide.scroll.bar.expand.animation")
           ? super.getTrackOffset(offset)
           : offset;
  }
}
