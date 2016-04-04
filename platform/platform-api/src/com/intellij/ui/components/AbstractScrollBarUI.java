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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane.Alignment;
import com.intellij.util.NotNullProducer;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.RegionPainter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static com.intellij.ui.components.JBScrollPane.BRIGHTNESS_FROM_VIEW;
import static java.awt.Adjustable.VERTICAL;

/**
 * @author Sergey.Malenkov
 */
abstract class AbstractScrollBarUI extends ScrollBarUI {
  static final Key<RegionPainter<Object>> LEADING_AREA = Key.create("PLAIN_SCROLL_BAR_UI_LEADING_AREA");//TODO:support

  private final Listener myListener = new Listener();
  private final Timer myScrollTimer = UIUtil.createNamedTimer("ScrollBarThumbScrollTimer", 60, myListener);

  private final Rectangle myThumbBounds = new Rectangle();
  private final Rectangle myTrackBounds = new Rectangle();
  private final Rectangle myLeadingBounds = new Rectangle();

  private JScrollBar myScrollBar;

  private boolean isTrackVisible;
  private boolean isValueCached;
  private int myCachedValue;

  abstract int getThickness();

  abstract int getMinimalThickness();

  abstract void onTrackHover(boolean hover);

  abstract void onThumbHover(boolean hover);

  abstract void paintTrack(Graphics2D g, int x, int y, int width, int height, JComponent c);

  abstract void paintThumb(Graphics2D g, int x, int y, int width, int height, JComponent c);

  void setTrackVisible(boolean trackVisible) {
    if (isTrackVisible != trackVisible) {
      isTrackVisible = trackVisible;
      repaint();
    }
  }

  void repaint() {
    if (myScrollBar != null) myScrollBar.repaint();
  }

  void repaint(int x, int y, int width, int height) {
    if (myScrollBar != null) myScrollBar.repaint(x, y, width, height);
  }

  boolean isOpaque() {
    return myScrollBar != null && myScrollBar.isOpaque();
  }

  boolean isAbsolutePositioning(MouseEvent event) {
    return SwingUtilities.isMiddleMouseButton(event);
  }

  int scale(int value) {
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
    boolean auto = Registry.is("ide.scroll.background.auto");
    myScrollBar.setBackground(auto ? null : new JBColor(new ColorProducer(c, 0xF5F5F5, 0x3C3F41)));
    myScrollBar.setForeground(auto ? null : new JBColor(new ColorProducer(c, 0xE6E6E6, 0x3C3F41)));
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
    return alignment == Alignment.LEFT || alignment == Alignment.RIGHT
           ? new Dimension(thickness, thickness * 2)
           : new Dimension(thickness * 2, thickness);
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    Alignment alignment = Alignment.get(c);
    if (alignment != null && g instanceof Graphics2D) {
      Rectangle bounds = new Rectangle(c.getSize());
      JBInsets.removeFrom(bounds, c.getInsets());
      if (c.isOpaque() && c.getParent() instanceof JScrollPane) {
        if (Registry.is("ide.scroll.track.border.paint")) {
          Color foreground = c.getForeground();
          if (foreground != null && !foreground.equals(c.getBackground())) {
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
      }
      else if (isTrackVisible) {
        paintTrack((Graphics2D)g, bounds.x, bounds.y, bounds.width, bounds.height, c);
      }
      // process a square area before the track
      RegionPainter<Object> leading = UIUtil.getClientProperty(c, LEADING_AREA);
      if (leading == null) {
        myLeadingBounds.setSize(0, 0);
      }
      else if (alignment == Alignment.LEFT || alignment == Alignment.RIGHT) {
        int size = bounds.width;
        myLeadingBounds.setBounds(bounds.x, bounds.y, size, size);
        leading.paint((Graphics2D)g, bounds.x, bounds.y, size, size, null);
        bounds.height -= size;
        bounds.y += size;
      }
      else {
        int size = bounds.height;
        myLeadingBounds.setBounds(bounds.x, bounds.y, size, size);
        leading.paint((Graphics2D)g, bounds.x, bounds.y, size, size, null);
        bounds.width -= size;
        bounds.x += size;
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
    int min = myScrollBar.getMinimum();
    int max = myScrollBar.getMaximum();
    int range = max - min;
    if (range <= 0) {
      myThumbBounds.setBounds(0, 0, 0, 0);
    }
    else if (VERTICAL == myScrollBar.getOrientation()) {
      int extent = myScrollBar.getVisibleAmount();
      int height = Math.max(myTrackBounds.height * extent / range, 2 * getThickness());
      if (myTrackBounds.height <= height) {
        myThumbBounds.setBounds(0, 0, 0, 0);
      }
      else {
        int value = getValue();
        int maxY = myTrackBounds.y + myTrackBounds.height - height;
        int y = (value < max - extent) ? (myTrackBounds.height - height) * (value - min) / (range - extent) : maxY;
        myThumbBounds.setBounds(myTrackBounds.x, adjust(y, myTrackBounds.y, maxY), myTrackBounds.width, height);
      }
    }
    else {
      int extent = myScrollBar.getVisibleAmount();
      int width = Math.max(myTrackBounds.width * extent / range, 2 * getThickness());
      if (myTrackBounds.width <= width) {
        myThumbBounds.setBounds(0, 0, 0, 0);
      }
      else {
        int value = getValue();
        int maxX = myTrackBounds.x + myTrackBounds.width - width;
        int x = (value < max - extent) ? (myTrackBounds.width - width) * (value - min) / (range - extent) : maxX;
        if (!myScrollBar.getComponentOrientation().isLeftToRight()) x = myTrackBounds.x - x + maxX;
        myThumbBounds.setBounds(adjust(x, myTrackBounds.x, maxX), myTrackBounds.y, width, myTrackBounds.height);
      }
    }
  }

  private int getValue() {
    return isValueCached ? myCachedValue : myScrollBar.getValue();
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
      if (myTrackBounds.contains(x, y)) {
        if (!isOverTrack) onTrackHover(isOverTrack = true);
        boolean hover = myThumbBounds.contains(x, y);
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

    @Override
    public void mousePressed(MouseEvent event) {
      if (myScrollBar == null || !myScrollBar.isEnabled()) return;
      if (SwingUtilities.isRightMouseButton(event)) return;

      isValueCached = true;
      myCachedValue = myScrollBar.getValue();
      myScrollBar.setValueIsAdjusting(true);

      myMouseX = event.getX();
      myMouseY = event.getY();

      boolean vertical = VERTICAL == myScrollBar.getOrientation();
      if (myThumbBounds.contains(myMouseX, myMouseY)) {
        // pressed on the thumb
        myOffset = vertical ? (myMouseY - myThumbBounds.y) : (myMouseX - myThumbBounds.x);
        isDragging = true;
      }
      else if (isTrackVisible && myTrackBounds.contains(myMouseX, myMouseY)) {
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
        myCachedValue = valueMin + valueRange * thumbValue / thumbRange;
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

  static boolean isDark(JComponent c) {
    if (c instanceof JScrollBar) {
      Container parent = c.getParent();
      if (parent instanceof JScrollPane) {
        JScrollPane pane = (JScrollPane)parent;
        Object property = c.getClientProperty(BRIGHTNESS_FROM_VIEW);
        if (property == null) {
          property = pane.getClientProperty(BRIGHTNESS_FROM_VIEW);
        }
        if (property instanceof Boolean && (Boolean)property) {
          JViewport viewport = pane.getViewport();
          if (viewport != null) {
            Component view = viewport.getView();
            if (view != null) {
              Color color = view.getBackground();
              if (color != null) {
                return ColorUtil.isDark(color);
              }
            }
          }
        }
      }
    }
    return UIUtil.isUnderDarcula();
  }

  private final class ColorProducer implements NotNullProducer<Color> {
    private final JComponent myComponent;
    private final Color myBrightColor;
    private final Color myDarkColor;

    @SuppressWarnings("UseJBColor")
    private ColorProducer(JComponent component, int bright, int dark) {
      myComponent = component;
      myBrightColor = new Color(bright);
      myDarkColor = new Color(dark);
    }

    @NotNull
    @Override
    public Color produce() {
      return isDark(myComponent) ? myDarkColor : myBrightColor;
    }
  }
}