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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SystemProperties;
import org.intellij.lang.annotations.MagicConstant;

import javax.swing.*;
import javax.swing.plaf.ScrollPaneUI;
import javax.swing.plaf.basic.BasicScrollPaneUI;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.lang.reflect.Field;

import static java.lang.Math.*;

/**
 * Scroll pane that can handle high-precision mouse wheel events.
 * <p>
 * Although Java 7 introduced {@link MouseWheelEvent#getPreciseWheelRotation()} method,
 * {@link JScrollPane} doesn't use it so far.
 *
 * @see BasicScrollPaneUI.Handler#mouseWheelMoved(MouseWheelEvent)
 * @see javax.swing.plaf.basic.BasicScrollBarUI#scrollByUnits
 */
public class SmoothScrollPane extends JScrollPane {
  private static final Logger LOG = Logger.getInstance(SmoothScrollPane.class);
  private static final double EPSILON = 1E-5D;

  public SmoothScrollPane() {
  }

  public SmoothScrollPane(Component view) {
    super(view);
  }

  public SmoothScrollPane(int vsbPolicy, int hsbPolicy) {
    super(vsbPolicy, hsbPolicy);
  }

  public SmoothScrollPane(Component view, int vsbPolicy, int hsbPolicy) {
    super(view, vsbPolicy, hsbPolicy);
  }

  @Override
  public void setUI(ScrollPaneUI ui) {
    super.setUI(ui);

    if (ui instanceof BasicScrollPaneUI) {
      try {
        Field field = BasicScrollPaneUI.class.getDeclaredField("mouseScrollListener");
        field.setAccessible(true);

        Object value = field.get(ui);

        if (value instanceof MouseWheelListener) {
          MouseWheelListener oldListener = (MouseWheelListener)value;
          MouseWheelListener newListener = e -> handleMouseWheelEvent(e, oldListener);
          field.set(ui, newListener);
          removeMouseWheelListener(oldListener);
          addMouseWheelListener(newListener);
        }
      }
      catch (Exception exception) {
        LOG.warn(exception);
      }
    }
  }

  private void handleMouseWheelEvent(MouseWheelEvent e, MouseWheelListener delegate) {
    if (SystemProperties.isTrueSmoothScrollingEnabled() && ComponentSettings.getInstance().isSmoothScrollingEligible() &&
        isWheelScrollingEnabled() && e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {

      mouseWheelMoved(e);
      e.consume();
    }
    else {
      delegate.mouseWheelMoved(e);
    }
  }

  private void mouseWheelMoved(MouseWheelEvent e) {
    JScrollBar scrollbar = e.isShiftDown() ? getHorizontalScrollBar() : getVerticalScrollBar();

    @MagicConstant(intValues = {SwingConstants.HORIZONTAL, SwingConstants.VERTICAL})
    int orientation = scrollbar.getOrientation();

    JViewport viewport = getViewport();

    if (viewport != null && (viewport.getView() instanceof Scrollable)) {
      Scrollable view = (Scrollable)(viewport.getView());

      double rotation = e.getPreciseWheelRotation();

      // Use (0, 0) view position to obtain constant unit increment (which might otherwise be variable on smaller-than-unit scrolling).
      Rectangle r = new Rectangle(new Point(0, 0), viewport.getViewSize());
      int unitIncrement = view.getScrollableUnitIncrement(r, orientation, 1);

      double delta = rotation * e.getScrollAmount() * unitIncrement;

      boolean limitDelta = abs(rotation) < 1.0D + EPSILON;
      int blockIncrement = view.getScrollableBlockIncrement(r, orientation, 1);
      double adjustedDelta = limitDelta ? max(-(double)blockIncrement, min(delta, (double)blockIncrement)) : delta;

      int value = scrollbar.getValue();
      int newValue = max(scrollbar.getMinimum(), min((int)round(value + adjustedDelta), scrollbar.getMaximum()));

      if (newValue != value) {
        scrollbar.setValue(newValue);
      }
    }
  }
}
