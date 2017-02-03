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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.ui.ComponentSettings;
import com.intellij.ui.InputSource;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ScrollPaneUI;
import javax.swing.plaf.basic.BasicScrollPaneUI;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.lang.reflect.Field;

import static java.lang.Math.*;

/**
 * Scroll pane that supports high-precision mouse wheel events (including pixel-perfect ones) and input interpolation.
 */
public class SmoothScrollPane extends JScrollPane {
  private static final Logger LOG = Logger.getInstance(SmoothScrollPane.class);
  private static final double EPSILON = 1E-5D;

  private static final RegistryValue SCROLLBAR_DELAY = Registry.get("idea.true.smooth.scrolling.interpolation.scrollbar.delay");
  private static final RegistryValue PRECISION_TOUCHPAD_DELAY = Registry.get("idea.true.smooth.scrolling.interpolation.precision.touchpad.delay");
  private static final RegistryValue MOUSE_WHEEL_MIN_DELAY = Registry.get("idea.true.smooth.scrolling.interpolation.mouse.wheel.delay.min");
  private static final RegistryValue MOUSE_WHEEL_MAX_DELAY = Registry.get("idea.true.smooth.scrolling.interpolation.mouse.wheel.delay.max");
  private static final RegistryValue DEFAULT_DELAY = Registry.get("idea.true.smooth.scrolling.interpolation.other.delay");

  private InputSource myInputSource = InputSource.UNKNOWN;
  private double myWheelRotation;

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
  protected void processMouseWheelEvent(MouseWheelEvent e) {
    boolean hasAbsoluteDelta = ComponentSettings.getInstance().isPixelPerfectScrollingEnabled() &&
                               MouseWheelEventEx.getAbsoluteDelta(e) != 0.0D;
    myInputSource = hasAbsoluteDelta ? InputSource.PRECISION_TOUCHPAD : InputSource.MOUSE_WHEEL;
    myWheelRotation = e.getPreciseWheelRotation();
    super.processMouseWheelEvent(e);
    myInputSource = InputSource.UNKNOWN;
  }

  @Override
  public JScrollBar createVerticalScrollBar() {
    return new SmoothScrollBar(Adjustable.VERTICAL);
  }

  @Override
  public JScrollBar createHorizontalScrollBar() {
    return new SmoothScrollBar(Adjustable.HORIZONTAL);
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
    ComponentSettings settings = ComponentSettings.getInstance();

    if (settings.isTrueSmoothScrollingEligibleFor(this) &&
        settings.isHighPrecisionScrollingEnabled() &&
        isWheelScrollingEnabled() && e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {

      mouseWheelMoved(e);
      e.consume();
    }
    else {
      delegate.mouseWheelMoved(e);
    }
  }

  /**
   * Although Java 7 introduced {@link MouseWheelEvent#getPreciseWheelRotation} method,
   * {@link JScrollPane} doesn't use it so far. This methods handles the precise wheel rotation events.
   * <p>
   * Additionally, to support pixel-perfect deltas, the handler can be paired up with our custom JRE which
   * <a href="https://github.com/JetBrains/jdk8u_jdk/commit/568f2dae82b0fe27b79ce6943071d89463758610">enhances MouseWheelEvent</a>
   * and <a href="https://github.com/JetBrains/jdk8u_jdk/commit/a3cb8807b148879e9c70a74a8a16c30a28991581">implements</a>
   * those events in Mac OS X.
   *
   * @see BasicScrollPaneUI.Handler#mouseWheelMoved(MouseWheelEvent)
   * @see javax.swing.plaf.basic.BasicScrollBarUI#scrollByUnits
   */
  private void mouseWheelMoved(MouseWheelEvent e) {
    JScrollBar scrollbar = e.isShiftDown() ? getHorizontalScrollBar() : getVerticalScrollBar();

    double delta = MouseWheelEventEx.getAbsoluteDelta(e);
    if (delta == 0.0D) {
      delta = getRelativeDelta(e, scrollbar);
    }

    int value = scrollbar instanceof Interpolable ? (((Interpolable)scrollbar).getTargetValue()) : scrollbar.getValue();
    double minDelta = (double)scrollbar.getMinimum() - value;
    double maxDelta = (double)scrollbar.getMaximum() - scrollbar.getModel().getExtent() - value;
    double boundedDelta = max(minDelta, min(delta, maxDelta));

    if (scrollbar instanceof FinelyAdjustable) {
      ((FinelyAdjustable)scrollbar).adjustValue(boundedDelta);
    }
    else {
      scrollbar.setValue(value + (int)round(boundedDelta));
    }
  }

  private double getRelativeDelta(MouseWheelEvent e, JScrollBar scrollbar) {
    JViewport viewport = getViewport();

    double rotation = e.getPreciseWheelRotation();
    int direction = rotation < 0 ? -1 : 1;
    int unitIncrement = getUnitIncrement(viewport, scrollbar, direction);
    double delta = rotation * e.getScrollAmount() * unitIncrement;

    boolean adjustDelta = abs(rotation) < 1.0D + EPSILON;
    int blockIncrement = getBlockIncrement(viewport, scrollbar, direction);
    return adjustDelta ? max(-(double)blockIncrement, min(delta, (double)blockIncrement)) : delta;
  }

  private static int getUnitIncrement(JViewport viewport, JScrollBar scrollbar, int direction) {
    Scrollable scrollable = getScrollable(viewport);

    if (scrollable == null) {
      return scrollbar.getUnitIncrement(direction);
    }
    else {
      // Use (0, 0) view position to obtain constant unit increment (which might otherwise be variable on smaller-than-unit scrolling).
      Rectangle r = new Rectangle(new Point(0, 0), viewport.getViewSize());
      return scrollable.getScrollableUnitIncrement(r, scrollbar.getOrientation(), 1);
    }
  }

  private static int getBlockIncrement(JViewport viewport, JScrollBar scrollbar, int direction) {
    Scrollable scrollable = getScrollable(viewport);
    Rectangle r = new Rectangle(new Point(0, 0), viewport.getViewSize());
    return scrollable == null ? scrollbar.getBlockIncrement(direction)
                              : scrollable.getScrollableBlockIncrement(r, scrollbar.getOrientation(), 1);
  }

  @Nullable
  private static Scrollable getScrollable(JViewport viewport) {
    return viewport != null && (viewport.getView() instanceof Scrollable) ? (Scrollable)(viewport.getView()) : null;
  }

  public InputSource getInputSource(boolean valueIsAdjusting) {
    return valueIsAdjusting ? InputSource.SCROLLBAR : myInputSource;
  }

  public int getInitialDelay(InputSource source) {
    switch (source) {
      case SCROLLBAR:
        return SCROLLBAR_DELAY.asInteger();
      case PRECISION_TOUCHPAD:
        return PRECISION_TOUCHPAD_DELAY.asInteger();
      case MOUSE_WHEEL:
        return max(MOUSE_WHEEL_MIN_DELAY.asInteger(),
                   min((int)round(abs(myWheelRotation) * MOUSE_WHEEL_MAX_DELAY.asInteger()),
                       MOUSE_WHEEL_MAX_DELAY.asInteger()));
      default:
        return DEFAULT_DELAY.asInteger();
    }
  }

  protected class SmoothScrollBar extends ScrollBar implements Interpolable, FinelyAdjustable {
    private final Interpolator myInterpolator = new Interpolator(this::getValue, this::setCurrentValue);
    private final Adjuster myAdjuster = new Adjuster(delta -> setValue(getTargetValue() + delta));

    protected SmoothScrollBar(int orientation) {
      super(orientation);
      if (SystemProperties.isTrueSmoothScrollingEnabled()) {
        setModel(new SmoothBoundedRangeModel(this));
      }
    }

    @Override
    public void setValue(int value) {
      ComponentSettings settings = ComponentSettings.getInstance();

      InputSource source = getInputSource(getValueIsAdjusting());

      if (settings.isTrueSmoothScrollingEligibleFor(getViewport().getView()) &&
          settings.isInterpolationEligibleFor(this) &&
          settings.isInterpolationEnabledFor(source)) {

        myInterpolator.setTarget(value, getInitialDelay(source));
      }
      else {
        super.setValue(value);
      }
    }

    @Override
    public void setCurrentValue(int value) {
      super.setValue(value);

      myAdjuster.reset();;
    }

    @Override
    public int getTargetValue() {
      return myInterpolator.getTarget();
    }

    // Support subpixel deltas
    @Override
    public void adjustValue(double delta) {
      myAdjuster.adjustValue(delta);
    }
  }
}
