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

import com.intellij.openapi.util.Key;
import com.intellij.ui.components.JBScrollPane.Alignment;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MouseEventAdapter;
import com.intellij.util.ui.RegionPainter;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static java.awt.Adjustable.VERTICAL;

/**
 * @author Sergey.Malenkov
 */
class DefaultScrollBarUI extends ScrollBarUI {
  static final Key<Component> LEADING = Key.create("JB_SCROLL_BAR_LEADING_COMPONENT");
  static final Key<Component> TRAILING = Key.create("JB_SCROLL_BAR_TRAILING_COMPONENT");

  private final Listener myListener = new Listener();
  private final Timer myScrollTimer = UIUtil.createNamedTimer("ScrollBarThumbScrollTimer", 60, myListener);

  final TwoWayAnimator myTrackAnimator = new TwoWayAnimator("ScrollBarTrack", 11, 150, 125, 300, 125) {
    @Override
    void onValueUpdate() {
      repaint();
    }
  };
  final TwoWayAnimator myThumbAnimator = new TwoWayAnimator("ScrollBarThumb", 11, 150, 125, 300, 125) {
    @Override
    void onValueUpdate() {
      repaint();
    }
  };

  private final Rectangle myThumbBounds = new Rectangle();
  private final Rectangle myTrackBounds = new Rectangle();
  private final int myThickness;
  private final int myThicknessMax;
  private final int myThicknessMin;

  JScrollBar myScrollBar;

  private boolean isValueCached;
  private int myCachedValue;
  private int myOldValue;

  DefaultScrollBarUI() {
    this(ScrollSettings.isThumbSmallIfOpaque() ? 13 : 10, 14, 10);
  }

  DefaultScrollBarUI(int thickness, int thicknessMax, int thicknessMin) {
    myThickness = thickness;
    myThicknessMax = thicknessMax;
    myThicknessMin = thicknessMin;
  }

  int getThickness() {
    return scale(myScrollBar == null || isOpaque(myScrollBar) ? myThickness : myThicknessMax);
  }

  int getMinimalThickness() {
    return scale(myThicknessMin);
  }

  static boolean isOpaque(JComponent c) {
    if (c.isOpaque()) return true;
    Container parent = c.getParent();
    // do not allow non-opaque scroll bars, because default layout does not support them
    return parent instanceof JScrollPane && parent.getLayout() instanceof ScrollPaneLayout.UIResource;
  }

  boolean isAbsolutePositioning(MouseEvent event) {
    return SwingUtilities.isMiddleMouseButton(event);
  }

  boolean isBorderNeeded(JComponent c) {
    return false;
  }

  boolean isTrackClickable() {
    return isOpaque(myScrollBar) || myTrackAnimator.myValue > 0;
  }

  boolean isTrackExpandable() {
    return false;
  }

  boolean isTrackContains(int x, int y) {
    return myTrackBounds.contains(x, y);
  }

  boolean isThumbContains(int x, int y) {
    return myThumbBounds.contains(x, y);
  }

  void onTrackHover(boolean hover) {
    myTrackAnimator.start(hover);
  }

  void onThumbHover(boolean hover) {
    myThumbAnimator.start(hover);
  }

  void paintTrack(Graphics2D g, int x, int y, int width, int height, JComponent c) {
    RegionPainter<Float> p = ScrollColorProducer.isDark(c) ? ScrollPainter.Track.DARCULA : ScrollPainter.Track.DEFAULT;
    paint(p, g, x, y, width, height, c, myTrackAnimator.myValue, false);
  }

  void paintThumb(Graphics2D g, int x, int y, int width, int height, JComponent c) {
    RegionPainter<Float> p = ScrollColorProducer.isDark(c) ? ScrollPainter.Thumb.DARCULA : ScrollPainter.Thumb.DEFAULT;
    paint(p, g, x, y, width, height, c, myThumbAnimator.myValue, ScrollSettings.isThumbSmallIfOpaque());
  }

  void onThumbMove() {
  }

  void paint(RegionPainter<Float> p, Graphics2D g, int x, int y, int width, int height, JComponent c, float value, boolean small) {
    if (!isOpaque(c)) {
      Alignment alignment = Alignment.get(c);
      if (alignment == Alignment.LEFT || alignment == Alignment.RIGHT) {
        int offset = getTrackOffset(width - getMinimalThickness());
        if (offset > 0) {
          width -= offset;
          if (alignment == Alignment.RIGHT) x += offset;
        }
      }
      else {
        int offset = getTrackOffset(height - getMinimalThickness());
        if (offset > 0) {
          height -= offset;
          if (alignment == Alignment.BOTTOM) y += offset;
        }
      }
    }
    else if (small) {
      x += 1;
      y += 1;
      width -= 2;
      height -= 2;
    }
    p.paint(g, x, y, width, height, value);
  }

  private int getTrackOffset(int offset) {
    if (!isTrackExpandable()) return offset;
    float value = myTrackAnimator.myValue;
    if (value <= 0) return offset;
    if (value >= 1) return 0;
    return (int)(.5f + offset * (1 - value));
  }

  void repaint() {
    if (myScrollBar != null) myScrollBar.repaint();
  }

  void repaint(int x, int y, int width, int height) {
    if (myScrollBar != null) myScrollBar.repaint(x, y, width, height);
  }

  private int scale(int value) {
    value = JBUI.scale(value);
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (UIUtil.getComponentStyle(myScrollBar)) {
      case LARGE:
        return (int)(value * 1.15);
      case SMALL:
        return (int)(value * 0.857);
      case MINI:
        return (int)(value * 0.714);
    }
    return value;
  }

  @Override
  public void installUI(JComponent c) {
    myScrollBar = (JScrollBar)c;
    ScrollColorProducer.setBackground(c);
    ScrollColorProducer.setForeground(c);
    myScrollBar.setOpaque(false);
    myScrollBar.setFocusable(false);
    myScrollBar.addMouseListener(myListener);
    myScrollBar.addMouseMotionListener(myListener);
    myScrollBar.getModel().addChangeListener(myListener);
    myScrollBar.addPropertyChangeListener(myListener);
    myScrollBar.addFocusListener(myListener);
    myScrollTimer.setInitialDelay(300);
  }

  @Override
  public void uninstallUI(JComponent c) {
    myScrollTimer.stop();
    myTrackAnimator.stop();
    myThumbAnimator.stop();
    myScrollBar.removeFocusListener(myListener);
    myScrollBar.removePropertyChangeListener(myListener);
    myScrollBar.getModel().removeChangeListener(myListener);
    myScrollBar.removeMouseMotionListener(myListener);
    myScrollBar.removeMouseListener(myListener);
    myScrollBar.setForeground(null);
    myScrollBar.setBackground(null);
    myScrollBar = null;
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    int thickness = getThickness();
    Alignment alignment = Alignment.get(c);
    Dimension preferred = new Dimension(thickness, thickness);
    if (alignment == Alignment.LEFT || alignment == Alignment.RIGHT) {
      preferred.height += preferred.height;
      addPreferredHeight(preferred, UIUtil.getClientProperty(myScrollBar, LEADING));
      addPreferredHeight(preferred, UIUtil.getClientProperty(myScrollBar, TRAILING));
    }
    else {
      preferred.width += preferred.width;
      addPreferredWidth(preferred, UIUtil.getClientProperty(myScrollBar, LEADING));
      addPreferredWidth(preferred, UIUtil.getClientProperty(myScrollBar, TRAILING));
    }
    return preferred;
  }

  private static void addPreferredWidth(Dimension preferred, Component component) {
    if (component != null) {
      Dimension size = component.getPreferredSize();
      preferred.width += size.width;
      if (preferred.height < size.height) preferred.height = size.height;
    }
  }

  private static void addPreferredHeight(Dimension preferred, Component component) {
    if (component != null) {
      Dimension size = component.getPreferredSize();
      preferred.height += size.height;
      if (preferred.width < size.width) preferred.width = size.width;
    }
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    Alignment alignment = Alignment.get(c);
    if (alignment != null && g instanceof Graphics2D) {
      Container parent = c.getParent();
      Color background = !isOpaque(c) ? null : c.getBackground();
      if (background != null) {
        g.setColor(background);
        g.fillRect(0, 0, c.getWidth(), c.getHeight());
      }
      Rectangle bounds = new Rectangle(c.getWidth(), c.getHeight());
      JBInsets.removeFrom(bounds, c.getInsets());
      // process an area before the track
      Component leading = UIUtil.getClientProperty(c, LEADING);
      if (leading != null) {
        if (alignment == Alignment.LEFT || alignment == Alignment.RIGHT) {
          int size = leading.getPreferredSize().height;
          leading.setBounds(bounds.x, bounds.y, bounds.width, size);
          bounds.height -= size;
          bounds.y += size;
        }
        else {
          int size = leading.getPreferredSize().width;
          leading.setBounds(bounds.x, bounds.y, size, bounds.height);
          bounds.width -= size;
          bounds.x += size;
        }
      }
      // process an area after the track
      Component trailing = UIUtil.getClientProperty(c, TRAILING);
      if (trailing != null) {
        if (alignment == Alignment.LEFT || alignment == Alignment.RIGHT) {
          int size = trailing.getPreferredSize().height;
          bounds.height -= size;
          trailing.setBounds(bounds.x, bounds.y + bounds.height, bounds.width, size);
        }
        else {
          int size = trailing.getPreferredSize().width;
          bounds.width -= size;
          trailing.setBounds(bounds.x + bounds.width, bounds.y, size, bounds.height);
        }
      }
      if (parent instanceof JScrollPane) {
        Color foreground = c.getForeground();
        if (foreground != null && !foreground.equals(background) && isBorderNeeded(c)) {
          g.setColor(foreground);
          switch (alignment) {
            case TOP:
              bounds.height--;
              g.drawLine(bounds.x, bounds.y + bounds.height, bounds.x + bounds.width, bounds.y + bounds.height);
              break;
            case LEFT:
              bounds.width--;
              g.drawLine(bounds.x + bounds.width, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height);
              break;
            case RIGHT:
              g.drawLine(bounds.x, bounds.y, bounds.x, bounds.y + bounds.height);
              bounds.width--;
              bounds.x++;
              break;
            case BOTTOM:
              g.drawLine(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y);
              bounds.height--;
              bounds.y++;
              break;
          }
        }
      }
      if (!isOpaque(c) && myTrackAnimator.myValue > 0) {
        paintTrack((Graphics2D)g, bounds.x, bounds.y, bounds.width, bounds.height, c);
      }
      myTrackBounds.setBounds(bounds);
      updateThumbBounds();
      // process additional drawing on the track
      RegionPainter<Object> track = UIUtil.getClientProperty(c, JBScrollBar.TRACK);
      if (track != null && myTrackBounds.width > 0 && myTrackBounds.height > 0) {
        track.paint((Graphics2D)g, myTrackBounds.x, myTrackBounds.y, myTrackBounds.width, myTrackBounds.height, null);
      }
      // process drawing the thumb
      if (myThumbBounds.width > 0 && myThumbBounds.height > 0) {
        paintThumb((Graphics2D)g, myThumbBounds.x, myThumbBounds.y, myThumbBounds.width, myThumbBounds.height, c);
      }
    }
  }

  private void updateThumbBounds() {
    int value = 0;
    int min = myScrollBar.getMinimum();
    int max = myScrollBar.getMaximum();
    int range = max - min;
    if (range <= 0) {
      myThumbBounds.setBounds(0, 0, 0, 0);
    }
    else if (VERTICAL == myScrollBar.getOrientation()) {
      int extent = myScrollBar.getVisibleAmount();
      int height = Math.max(convert(myTrackBounds.height, extent, range), 2 * getThickness());
      if (myTrackBounds.height <= height) {
        myThumbBounds.setBounds(0, 0, 0, 0);
      }
      else {
        value = getValue();
        int maxY = myTrackBounds.y + myTrackBounds.height - height;
        int y = (value < max - extent) ? convert(myTrackBounds.height - height, value - min, range - extent) : maxY;
        myThumbBounds.setBounds(myTrackBounds.x, adjust(y, myTrackBounds.y, maxY), myTrackBounds.width, height);
        if (myOldValue != value) onThumbMove();
      }
    }
    else {
      int extent = myScrollBar.getVisibleAmount();
      int width = Math.max(convert(myTrackBounds.width, extent, range), 2 * getThickness());
      if (myTrackBounds.width <= width) {
        myThumbBounds.setBounds(0, 0, 0, 0);
      }
      else {
        value = getValue();
        int maxX = myTrackBounds.x + myTrackBounds.width - width;
        int x = (value < max - extent) ? convert(myTrackBounds.width - width, value - min, range - extent) : maxX;
        if (!myScrollBar.getComponentOrientation().isLeftToRight()) x = myTrackBounds.x - x + maxX;
        myThumbBounds.setBounds(adjust(x, myTrackBounds.x, maxX), myTrackBounds.y, width, myTrackBounds.height);
        if (myOldValue != value) onThumbMove();
      }
    }
    myOldValue = value;
  }

  private int getValue() {
    return isValueCached ? myCachedValue : myScrollBar.getValue();
  }

  /**
   * Converts a value from old range to new one.
   * It is necessary to use floating point calculation to avoid integer overflow.
   */
  private static int convert(double newRange, double oldValue, double oldRange) {
    return (int)(.5 + newRange * oldValue / oldRange);
  }

  private static int adjust(int value, int min, int max) {
    return value < min ? min : value > max ? max : value;
  }

  private final class Listener extends MouseAdapter implements ActionListener, FocusListener, ChangeListener, PropertyChangeListener {
    private int myOffset;
    private int myMouseX, myMouseY;
    private boolean isReversed;
    private boolean isDragging;
    private boolean isOverTrack;
    private boolean isOverThumb;

    private void updateMouse(int x, int y) {
      if (isTrackContains(x, y)) {
        if (!isOverTrack) onTrackHover(isOverTrack = true);
        boolean hover = isThumbContains(x, y);
        if (isOverThumb != hover) onThumbHover(isOverThumb = hover);
      }
      else {
        updateMouseExit();
      }
    }

    private void updateMouseExit() {
      if (isOverThumb) onThumbHover(isOverThumb = false);
      if (isOverTrack) onTrackHover(isOverTrack = false);
    }

    private boolean redispatchIfTrackNotClickable(MouseEvent event) {
      if (isTrackClickable()) return false;
      // redispatch current event to the view
      Container parent = myScrollBar.getParent();
      if (parent instanceof JScrollPane) {
        JScrollPane pane = (JScrollPane)parent;
        Component view = pane.getViewport().getView();
        if (view != null) {
          Point point = event.getLocationOnScreen();
          SwingUtilities.convertPointFromScreen(point, view);
          Component target = SwingUtilities.getDeepestComponentAt(view, point.x, point.y);
          if (target != null) target.dispatchEvent(MouseEventAdapter.convert(event, target));
        }
      }
      return true;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (myScrollBar != null && myScrollBar.isEnabled()) redispatchIfTrackNotClickable(e);
    }

    @Override
    public void mousePressed(MouseEvent event) {
      if (myScrollBar == null || !myScrollBar.isEnabled()) return;
      if (redispatchIfTrackNotClickable(event)) return;
      if (SwingUtilities.isRightMouseButton(event)) return;

      isValueCached = true;
      myCachedValue = myScrollBar.getValue();
      myScrollBar.setValueIsAdjusting(true);

      myMouseX = event.getX();
      myMouseY = event.getY();

      boolean vertical = VERTICAL == myScrollBar.getOrientation();
      if (isThumbContains(myMouseX, myMouseY)) {
        // pressed on the thumb
        myOffset = vertical ? (myMouseY - myThumbBounds.y) : (myMouseX - myThumbBounds.x);
        isDragging = true;
      }
      else if (isTrackContains(myMouseX, myMouseY)) {
        // pressed on the track
        if (isAbsolutePositioning(event)) {
          myOffset = (vertical ? myThumbBounds.height : myThumbBounds.width) / 2;
          isDragging = true;
          setValueFrom(event);
        }
        else {
          myScrollTimer.stop();
          isDragging = false;
          if (VERTICAL == myScrollBar.getOrientation()) {
            int y = myThumbBounds.isEmpty() ? myScrollBar.getHeight() / 2 : myThumbBounds.y;
            isReversed = myMouseY < y;
          }
          else {
            int x = myThumbBounds.isEmpty() ? myScrollBar.getWidth() / 2 : myThumbBounds.x;
            isReversed = myMouseX < x;
            if (!myScrollBar.getComponentOrientation().isLeftToRight()) {
              isReversed = !isReversed;
            }
          }
          scroll(isReversed);
          startScrollTimerIfNecessary();
        }
      }
    }

    @Override
    public void mouseReleased(MouseEvent event) {
      if (isDragging) updateMouse(event.getX(), event.getY());
      if (myScrollBar == null || !myScrollBar.isEnabled()) return;
      if (redispatchIfTrackNotClickable(event)) return;
      if (SwingUtilities.isRightMouseButton(event)) return;
      isDragging = false;
      myOffset = 0;
      myScrollTimer.stop();
      isValueCached = true;
      myCachedValue = myScrollBar.getValue();
      myScrollBar.setValueIsAdjusting(false);
      repaint();
    }

    @Override
    public void mouseDragged(MouseEvent event) {
      if (myScrollBar == null || !myScrollBar.isEnabled()) return;
      if (myThumbBounds.isEmpty() || SwingUtilities.isRightMouseButton(event)) return;
      if (isDragging) {
        setValueFrom(event);
      }
      else {
        myMouseX = event.getX();
        myMouseY = event.getY();
        updateMouse(myMouseX, myMouseY);
        startScrollTimerIfNecessary();
      }
    }

    @Override
    public void mouseMoved(MouseEvent event) {
      if (myScrollBar == null || !myScrollBar.isEnabled()) return;
      if (!isDragging) updateMouse(event.getX(), event.getY());
      redispatchIfTrackNotClickable(event);
    }

    @Override
    public void mouseExited(MouseEvent event) {
      if (myScrollBar == null || !myScrollBar.isEnabled()) return;
      if (!isDragging) updateMouseExit();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
      if (myScrollBar == null) {
        myScrollTimer.stop();
      }
      else {
        scroll(isReversed);
        if (!myThumbBounds.isEmpty()) {
          if (isReversed ? !isMouseBeforeThumb() : !isMouseAfterThumb()) {
            myScrollTimer.stop();
          }
        }
        int value = myScrollBar.getValue();
        if (isReversed ? value <= myScrollBar.getMinimum() : value >= myScrollBar.getMaximum() - myScrollBar.getVisibleAmount()) {
          myScrollTimer.stop();
        }
      }
    }

    @Override
    public void focusGained(FocusEvent event) {
      repaint();
    }

    @Override
    public void focusLost(FocusEvent event) {
      repaint();
    }

    @Override
    public void stateChanged(ChangeEvent event) {
      updateThumbBounds();
      // TODO: update mouse
      isValueCached = false;
      repaint();
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
      String name = event.getPropertyName();
      if ("model" == name) {
        BoundedRangeModel oldModel = (BoundedRangeModel)event.getOldValue();
        BoundedRangeModel newModel = (BoundedRangeModel)event.getNewValue();
        oldModel.removeChangeListener(this);
        newModel.addChangeListener(this);
      }
      if ("model" == name || "orientation" == name || "componentOrientation" == name) {
        repaint();
      }
      if ("opaque" == name || "visible" == name) {
        myTrackAnimator.rewind(false);
        myThumbAnimator.rewind(false);
        myTrackBounds.setBounds(0, 0, 0, 0);
        myThumbBounds.setBounds(0, 0, 0, 0);
      }
    }

    private void setValueFrom(MouseEvent event) {
      int x = event.getX();
      int y = event.getY();

      int thumbMin, thumbMax, thumbPos;
      if (VERTICAL == myScrollBar.getOrientation()) {
        thumbMin = myTrackBounds.y;
        thumbMax = myTrackBounds.y + myTrackBounds.height - myThumbBounds.height;
        thumbPos = Math.min(thumbMax, Math.max(thumbMin, (y - myOffset)));
        if (myThumbBounds.y != thumbPos) {
          int minY = Math.min(myThumbBounds.y, thumbPos);
          int maxY = Math.max(myThumbBounds.y, thumbPos) + myThumbBounds.height;
          myThumbBounds.y = thumbPos;
          onThumbMove();
          repaint(myThumbBounds.x, minY, myThumbBounds.width, maxY - minY);
        }
      }
      else {
        thumbMin = myTrackBounds.x;
        thumbMax = myTrackBounds.x + myTrackBounds.width - myThumbBounds.width;
        thumbPos = Math.min(thumbMax, Math.max(thumbMin, (x - myOffset)));
        if (myThumbBounds.x != thumbPos) {
          int minX = Math.min(myThumbBounds.x, thumbPos);
          int maxX = Math.max(myThumbBounds.x, thumbPos) + myThumbBounds.width;
          myThumbBounds.x = thumbPos;
          onThumbMove();
          repaint(minX, myThumbBounds.y, maxX - minX, myThumbBounds.height);
        }
      }
      int valueMin = myScrollBar.getMinimum();
      int valueMax = myScrollBar.getMaximum() - myScrollBar.getVisibleAmount();
      // If the thumb has reached the end of the scrollbar, then just set the value to its maximum.
      // Otherwise compute the value as accurately as possible.
      boolean isDefaultOrientation = VERTICAL == myScrollBar.getOrientation() || myScrollBar.getComponentOrientation().isLeftToRight();
      if (thumbPos == thumbMax) {
        myScrollBar.setValue(isDefaultOrientation ? valueMax : valueMin);
      }
      else {
        int valueRange = valueMax - valueMin;
        int thumbRange = thumbMax - thumbMin;
        int thumbValue = isDefaultOrientation
                         ? thumbPos - thumbMin
                         : thumbMax - thumbPos;
        isValueCached = true;
        myCachedValue = valueMin + convert(valueRange, thumbValue, thumbRange);
        myScrollBar.setValue(myCachedValue);
      }
      if (!isDragging) updateMouse(x, y);
    }

    private void startScrollTimerIfNecessary() {
      if (!myScrollTimer.isRunning()) {
        if (isReversed ? isMouseBeforeThumb() : isMouseAfterThumb()) {
          myScrollTimer.start();
        }
      }
    }

    private boolean isMouseBeforeThumb() {
      return VERTICAL == myScrollBar.getOrientation()
             ? isMouseOnTop()
             : myScrollBar.getComponentOrientation().isLeftToRight()
               ? isMouseOnLeft()
               : isMouseOnRight();
    }

    private boolean isMouseAfterThumb() {
      return VERTICAL == myScrollBar.getOrientation()
             ? isMouseOnBottom()
             : myScrollBar.getComponentOrientation().isLeftToRight()
               ? isMouseOnRight()
               : isMouseOnLeft();
    }

    private boolean isMouseOnTop() {
      return myMouseY < myThumbBounds.y;
    }

    private boolean isMouseOnLeft() {
      return myMouseX < myThumbBounds.x;
    }

    private boolean isMouseOnRight() {
      return myMouseX > myThumbBounds.x + myThumbBounds.width;
    }

    private boolean isMouseOnBottom() {
      return myMouseY > myThumbBounds.y + myThumbBounds.height;
    }

    private void scroll(boolean reversed) {
      int delta = myScrollBar.getBlockIncrement(reversed ? -1 : 1);
      if (reversed) delta = -delta;

      int oldValue = myScrollBar.getValue();
      int newValue = oldValue + delta;

      if (delta > 0 && newValue < oldValue) {
        newValue = myScrollBar.getMaximum();
      }
      else if (delta < 0 && newValue > oldValue) {
        newValue = myScrollBar.getMinimum();
      }
      if (oldValue != newValue) {
        myScrollBar.setValue(newValue);
      }
    }
  }
}
