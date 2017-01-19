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

import com.intellij.util.MethodInvocator;

import java.awt.event.MouseWheelEvent;

/**
 * Accessor for absolute scrolling delta in the
 * <a href="https://github.com/JetBrains/jdk8u_jdk/commit/568f2dae82b0fe27b79ce6943071d89463758610">enhanced MouseWheelEvent</a>.
 * <p>
 * Should be paired up with our custom JRE (currently has has
 * an <a href="https://github.com/JetBrains/jdk8u_jdk/commit/a3cb8807b148879e9c70a74a8a16c30a28991581">implementation</a> for Mac OS X).
 */
class MouseWheelEventEx {
  private static final MethodInvocator ourGetScrollingDeltaMethod = new MethodInvocator(false, MouseWheelEvent.class, "getScrollingDelta");

  /**
   * Returns scrolling delta as an absolute value (if available).
   * <p>
   * Returns 0.0 when scrolling delta is available only as a number of "clicks" or
   * as a fraction of a "click".
   * <p>
   * The method returns natural numbers, however, for future extensibility,
   * the return type is declared as {@code double}.
   * <p>
   * Some devices (e.g. high-precision touchpads) may report scrolling deltas
   * in absolute values rather than in fractions of a "click". This data can be used,
   * for example, to implement pixel-perfect scrolling (which cannot be
   * implemented via the {@link MouseWheelEvent#getPreciseWheelRotation} method, as fractions
   * of scrolling "units" cannot be reliably translated to pixel-precise deltas).
   *
   * @param event the event
   * @return negative values for scrolling up, positive values for scrolling down (natural numbers)
   */
  static double getScrollingDelta(MouseWheelEvent event) {
    return ourGetScrollingDeltaMethod.isAvailable() ? (double)ourGetScrollingDeltaMethod.invoke(event) : 0.0D;
  }
}
