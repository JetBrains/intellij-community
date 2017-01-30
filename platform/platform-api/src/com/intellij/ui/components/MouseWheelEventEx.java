/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import java.awt.event.MouseWheelEvent;

/**
 * Accessor for absolute scrolling delta in enhanced MouseWheelEvent.
 */
class MouseWheelEventEx {
  /**
   * Returns scrolling delta as an absolute value (if available). Requires JetBrains Runtime.
   * <p>
   * Some devices (e.g. high-precision touchpads) may report scrolling deltas
   * in absolute values rather than in fractions of a "click". This data can be used,
   * for example, to implement pixel-perfect scrolling (which cannot be
   * implemented via the {@link MouseWheelEvent#getPreciseWheelRotation} method, as fractions
   * of scrolling "units" cannot be reliably translated to pixel-precise deltas).
   * <p>
   * JetBrains Runtime currently uses <code>preciseWheelRotation = 0.1 * scrollingDelta</code>
   * mapping in Mac OS to encode absolute scrolling deltas as relative ones.
   * <p>
   * That approach is suboptimal, because it:
   * 1) produces different dynamics of relative deltas (in Mac OS there's no reliable linear mapping between those values),
   * 2) makes handling of true relative deltas impossible, as there's no way to distinguish them from the encoded absolute ones
   * (if we choose to decode the absolute deltas).
   * <p>
   * Ideally, we need to implement a method of distinguishing between the two kinds of scrolling deltas.
   *
   * @param e the event
   * @return negative values for scrolling up, positive values for scrolling down
   * 0.0 when absolute deltas are not supported or not available
   */
  static double getScrollingDelta(MouseWheelEvent e) {
    // This API is temporary disabled as there's no way to detect relative deltas.
    return 0.0D;//SystemInfo.isJetbrainsJvm && SystemInfo.isMac ? 10.0D * e.getPreciseWheelRotation() : 0.0D;
  }
}
