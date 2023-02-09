// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeGlassPane.TopComponent;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scroll.TouchScrollUtil;
import com.intellij.util.ui.RegionPainter;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ScrollBarUI;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * Our implementation of a scroll bar with the custom UI.
 * Also it provides a method to create custom UI for our custom L&Fs.
 *
 * @see #createUI(JComponent)
 */
public class JBScrollBar extends JScrollBar implements TopComponent, Interpolable {
  /**
   * This key defines a region painter, which is used by the custom ScrollBarUI
   * to draw additional paintings (i.e. error stripes) on the scrollbar's track.
   *
   * @see UIUtil#putClientProperty
   */
  public static final Key<RegionPainter<Object>> TRACK = Key.create("JB_SCROLL_BAR_TRACK");
  /**
   * This constraint should be used to add a component that will be shown before the scrollbar's track.
   * Note that the previously added leading component will be removed.
   *
   * @see #addImpl(Component, Object, int)
   */
  public static final String LEADING = "JB_SCROLL_BAR_LEADING_COMPONENT";
  /**
   * This constraint should be used to add a component that will be shown after the scrollbar's track.
   * Note that the previously added trailing component will be removed.
   *
   * @see #addImpl(Component, Object, int)
   */
  public static final String TRAILING = "JB_SCROLL_BAR_TRAILING_COMPONENT";

  private final Interpolator myInterpolator = new Interpolator(this::getValue, this::setCurrentValue);
  private double myFractionalRemainder;
  private boolean wasPositiveDelta;
  private boolean isUnitIncrementSet;
  private boolean isBlockIncrementSet;

  public JBScrollBar() {
    this(VERTICAL);
  }

  public JBScrollBar(@JdkConstants.AdjustableOrientation int orientation) {
    this(orientation, 0, 10, 0, 100);
  }

  public JBScrollBar(@JdkConstants.AdjustableOrientation int orientation, int value, int extent, int min, int max) {
    super(orientation, value, extent, min, max);
    setModel(new Model(value, extent, min, max));
    putClientProperty("JScrollBar.fastWheelScrolling", Boolean.TRUE); // fast scrolling for JDK 6
  }

  @Override
  protected void addImpl(Component component, Object name, int index) {
    Key<Component> key = LEADING.equals(name) ? DefaultScrollBarUI.LEADING : TRAILING.equals(name) ? DefaultScrollBarUI.TRAILING : null;
    if (key != null) {
      Component old = ComponentUtil.getClientProperty(this, key);
      ComponentUtil.putClientProperty(this, key, component);
      if (old != null) remove(old);
    }
    super.addImpl(component, name, index);
  }

  @Override
  public void updateUI() {
    ScrollBarUI ui = getUI();
    if (ui instanceof DefaultScrollBarUI) return;
    setUI(createUI(this, isThin()));
  }

  @SuppressWarnings("UnusedParameters")
  @NotNull
  public static ScrollBarUI createUI(JComponent c) {
    return createUI(c, false);
  }

  /**
   * Returns a new instance of {@link ScrollBarUI}.
   * Do not share it between different scroll bars.
   *
   * @param c a target component for this UI
   * @return a new instance of {@link ScrollBarUI}
   */
  @SuppressWarnings("UnusedParameters")
  @NotNull
  public static ScrollBarUI createUI(JComponent c, boolean isThin) {
    if (SystemInfo.isMac) {
      return isThin ? new ThinMacScrollBarUI() : new MacScrollBarUI();
    }
    else {
      return isThin ? new ThinScrollBarUI() : new DefaultScrollBarUI();
    }
  }

  /**
   * Computes the unit increment for scrolling if the viewport's view.
   * Otherwise returns {@code super.getUnitIncrement}.
   *
   * @param direction less than zero to scroll up/left, greater than zero for down/right
   * @return an integer, in pixels, containing the unit increment
   * @see Scrollable#getScrollableUnitIncrement
   */
  @Override
  public int getUnitIncrement(int direction) {
    JViewport viewport = getViewport();
    if (viewport != null) {
      Scrollable scrollable = getScrollableViewToCalculateIncrement(viewport.getView());
      if (scrollable != null) return scrollable.getScrollableUnitIncrement(viewport.getViewRect(), orientation, direction);
      if (!isUnitIncrementSet) {
        Font font = getViewFont(viewport);
        if (font != null) return font.getSize(); // increase default unit increment for fast scrolling
      }
    }
    return super.getUnitIncrement(direction);
  }

  @Override
  public void setUnitIncrement(int increment) {
    isUnitIncrementSet = true;
    super.setUnitIncrement(increment);
  }

  public void toggle(boolean isOn) {
    ScrollBarUI ui = getUI();
    if (ui instanceof DefaultScrollBarUI) {
      ((DefaultScrollBarUI)ui).toggle(isOn);
    }
  }

  /**
   * Computes the block increment for scrolling if the viewport's view.
   * Otherwise returns {@code super.getBlockIncrement}.
   *
   * @param direction less than zero to scroll up/left, greater than zero for down/right
   * @return an integer, in pixels, containing the block increment
   * @see Scrollable#getScrollableBlockIncrement
   */
  @Override
  public int getBlockIncrement(int direction) {
    JViewport viewport = getViewport();
    if (viewport != null) {
      Scrollable scrollable = getScrollableViewToCalculateIncrement(viewport.getView());
      if (scrollable != null) return scrollable.getScrollableBlockIncrement(viewport.getViewRect(), orientation, direction);
      if (!isBlockIncrementSet) {
        Dimension size = viewport.getExtentSize();
        return orientation == HORIZONTAL ? size.width : size.height;
      }
    }
    return super.getBlockIncrement(direction);
  }

  @Override
  public void setBlockIncrement(int increment) {
    isBlockIncrementSet = true;
    super.setBlockIncrement(increment);
  }

  /**
   * Notifies glass pane that it should not process mouse event above the scrollbar's thumb.
   *
   * @param event the mouse event
   * @return {@code true} if glass pane can process the specified event, {@code false} otherwise
   */
  @Override
  public boolean canBePreprocessed(@NotNull MouseEvent event) {
    return JBScrollPane.canBePreprocessed(event, this);
  }

  @Override
  public void setValue(int value) {
    int delay = 0;
    Component parent = getParent();
    if (parent instanceof JBScrollPane pane) {
      JViewport viewport = pane.getViewport();
      if (viewport != null && ScrollSettings.isEligibleFor(viewport.getView()) && ScrollSettings.isInterpolationEligibleFor(this)) {
        delay = pane.getInitialDelay(getValueIsAdjusting());
      }
    }
    if (delay > 0) {
      myInterpolator.setTarget(value, delay);
    }
    else {
      super.setValue(value);
    }
  }

  @Override
  public void setCurrentValue(int value) {
    super.setValue(value);
    myFractionalRemainder = 0.0;
  }

  @Override
  public int getTargetValue() {
    return myInterpolator.getTarget();
  }

  /**
   * Handles the mouse wheel events to scroll the scrollbar.
   *
   * @param event the mouse wheel event
   * @return {@code true} if the specified event is handled and consumed, {@code false} otherwise
   */
  public boolean handleMouseWheelEvent(MouseWheelEvent event) {
    if (!isSupportedScrollType(event)) return false;
    if (event.isShiftDown() == (orientation == VERTICAL)) return false;
    if (!ScrollSettings.isEligibleFor(this)) return false;

    double delta = getPreciseDelta(event);
    if (!Double.isFinite(delta)) return false;

    int value = getTargetValue();
    double deltaAdjusted = getDeltaAdjusted(value, delta);
    if (deltaAdjusted != 0.0) {
      boolean isPositiveDelta = deltaAdjusted > 0.0;
      if (wasPositiveDelta != isPositiveDelta) {
        // reset accumulator if scrolling direction is changed
        wasPositiveDelta = isPositiveDelta;
        myFractionalRemainder = 0.0;
      }
      deltaAdjusted += myFractionalRemainder;
      int valueAdjusted = (int)deltaAdjusted;
      if (valueAdjusted == 0) {
        myFractionalRemainder = deltaAdjusted;
      }
      else {
        myFractionalRemainder = deltaAdjusted - (double)valueAdjusted;
        setValue(value + valueAdjusted);
      }
    }
    else if (delta != 0.0) {
      return true; // do not consume event if it can be processed by parent component
    }
    event.consume();
    return true;
  }

  private static boolean isSupportedScrollType(MouseWheelEvent e) {
    return e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL || TouchScrollUtil.isUpdate(e);
  }

  private JViewport getViewport() {
    Component parent = getParent();
    if (parent instanceof JScrollPane pane) {
      return pane.getViewport();
    }
    return null;
  }

  private static Font getViewFont(JViewport viewport) {
    if (viewport == null) return null;
    Component view = viewport.getView();
    return view == null ? null : view.getFont();
  }

  private Scrollable getScrollableViewToCalculateIncrement(Component view) {
    return view instanceof JTable || (view instanceof Scrollable && orientation == VERTICAL) ? (Scrollable)view : null;
  }

  private static double boundDelta(double minDelta, double maxDelta, double delta) {
    return Math.max(minDelta, Math.min(maxDelta, delta));
  }

  protected double getDeltaAdjusted(MouseWheelEvent event) {
    int value = getTargetValue();
    double delta = getPreciseDelta(event);
    return getDeltaAdjusted(value, delta);
  }

  /**
   * Calculates adjusted delta for the bar.
   *
   * @param value the target value for the bar
   * @param delta the supposed delta
   * @return the delta itself or an adjusted delta
   */
  private double getDeltaAdjusted(int value, double delta) {
    double minDelta = getMinimum() - value;
    double maxDelta = getMaximum() - getVisibleAmount() - value;
    return Math.max(minDelta, Math.min(maxDelta, delta));
  }

  /**
   * Calculates a scrolling delta from the specified event.
   *
   * @param event the mouse wheel event
   * @return a scrolling delta for this scrollbar
   */
  private double getPreciseDelta(MouseWheelEvent event) {
    if (TouchScrollUtil.isTouchScroll(event)) {
      return TouchScrollUtil.getDelta(event);
    }
    double rotation = event.getPreciseWheelRotation();
    if (ScrollSettings.isPixelPerfectEnabled()) {
      // calculate an absolute delta if possible
      if (SystemInfo.isMac) {
        // Native code in our JDK for Mac uses 0.1 to convert pixels to units,
        // so we use 10 to restore amount of pixels to scroll.
        return 10 * rotation;
      }
      JViewport viewport = getViewport();
      Font font = viewport == null ? null : getViewFont(viewport);
      int size = font == null ? JBUIScale.scale(10) : font.getSize(); // assume an unit size
      return size * rotation * event.getScrollAmount();
    }
    if (ScrollSettings.isHighPrecisionEnabled()) {
      // calculate a relative delta if possible
      int direction = rotation < 0 ? -1 : 1;
      int unitIncrement = getUnitIncrement(direction);
      double delta = unitIncrement * rotation * event.getScrollAmount();
      // When the scrolling speed is set to maximum, it's possible to scroll by more units than will fit in the visible area.
      // To make for more accurate low-speed scrolling, we limit scrolling to the block increment
      // if the wheel was only rotated one click.
      double blockIncrement = getBlockIncrement(direction);
      return boundDelta(-blockIncrement, blockIncrement, delta);
    }
    return Double.NaN;
  }

  public boolean isThin() {
    return false;
  }

  private static final class Model extends DefaultBoundedRangeModel {
    private Model(int value, int extent, int min, int max) {
      super(value, extent, min, max);
    }

    /**
     * Implementation of {@link DefaultBoundedRangeModel#fireStateChanged()} with optional filtering.
     * It doesn't notify its scrollbar UI listener on value changes
     * while the scrollbar value is adjusted by dragging the scrollbar's thumb.
     */
    @Override
    protected void fireStateChanged() {
      if (getValueIsAdjusting() && ScrollSource.SCROLLBAR.isInterpolationEnabled()) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
          if (listeners[i] == ChangeListener.class) {
            ChangeListener listener = (ChangeListener)listeners[i + 1];
            // ignore listeners declared in different implementations of ScrollBarUI
            if (!listener.getClass().getName().contains("ScrollBarUI")) {
              if (changeEvent == null) changeEvent = new ChangeEvent(this);
              listener.stateChanged(changeEvent);
            }
          }
        }
      }
      else {
        super.fireStateChanged();
      }
    }
  }
}
