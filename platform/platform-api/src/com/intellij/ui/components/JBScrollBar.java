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
package com.intellij.ui.components;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.RegionPainter;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;

import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.plaf.ScrollBarUI;
import java.awt.Adjustable;

/**
 * Our implementation of a scroll bar with the custom UI.
 * Also it provides a method to create custom UI for our custom L&Fs.
 *
 * @see #createUI(JComponent)
 */
public class JBScrollBar extends JScrollBar {
  /**
   * This key defines a region painter, which is used by the custom ScrollBarUI
   * to draw additional paintings (i.e. error stripes) on the scrollbar's track.
   *
   * @see UIUtil#putClientProperty
   */
  public static final Key<RegionPainter<Object>> TRACK = Key.create("JB_SCROLL_BAR_TRACK");

  public JBScrollBar() {
    this(Adjustable.VERTICAL);
  }

  public JBScrollBar(@JdkConstants.AdjustableOrientation int orientation) {
    this(orientation, 0, 10, 0, 100);
  }

  public JBScrollBar(@JdkConstants.AdjustableOrientation int orientation, int value, int extent, int min, int max) {
    super(orientation, value, extent, min, max);
    putClientProperty("JScrollBar.fastWheelScrolling", Boolean.TRUE); // fast scrolling for JDK 6
  }

  @Override
  public void updateUI() {
    ScrollBarUI ui = getUI();
    if (ui instanceof DefaultScrollBarUI) return;
    setUI(createUI(this));
  }

  /**
   * Returns a new instance of {@link ScrollBarUI}.
   * Do not share it between different scroll bars.
   *
   * @param c a target component for this UI
   * @return a new instance of {@link ScrollBarUI}
   */
  @SuppressWarnings("UnusedParameters")
  public static ScrollBarUI createUI(JComponent c) {
    return SystemInfo.isMac ? new MacScrollBarUI() : new DefaultScrollBarUI();
  }
}
