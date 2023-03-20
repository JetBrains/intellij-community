// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.openapi.util.Key;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.components.JBScrollPane.Alignment;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.MathUtil;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static java.awt.Adjustable.VERTICAL;

class DefaultScrollBarUI extends ScrollBarUI {
  static final Key<Component> LEADING = Key.create("JB_SCROLL_BAR_LEADING_COMPONENT");
  static final Key<Component> TRAILING = Key.create("JB_SCROLL_BAR_TRAILING_COMPONENT");

  private final Listener myListener = new Listener();
  private final Timer myScrollTimer = TimerUtil.createNamedTimer("ScrollBarThumbScrollTimer", 60, myListener);

  private final int myThickness;
  private final int myThicknessMax;
  private final int myThicknessMin;

  JScrollBar myScrollBar;

  final ScrollBarPainter.Track myTrack = new ScrollBarPainter.Track(() -> myScrollBar);
  final ScrollBarPainter.Thumb myThumb = createThumbPainter();

  private boolean isValueCached;
  private int myCachedValue;
  private int myOldValue;

  protected final ScrollBarAnimationBehavior myAnimationBehavior;

  DefaultScrollBarUI() {
    this(ScrollSettings.isThumbSmallIfOpaque() ? 13 : 10, 14, 10);
  }

  DefaultScrollBarUI(int thickness, int thicknessMax, int thicknessMin) {
    myThickness = thickness;
    myThicknessMax = thicknessMax;
    myThicknessMin = thicknessMin;
    myAnimationBehavior = new ToggleableScrollBarAnimationBehaviorDecorator(createBaseAnimationBehavior(),
                                                                            myTrack.animator,
                                                                            myThumb.animator);
  }

  protected ScrollBarPainter.Thumb createThumbPainter() {
    return new ScrollBarPainter.Thumb(() -> myScrollBar, false);
  }

  protected ScrollBarAnimationBehavior createBaseAnimationBehavior() {
    return new DefaultScrollBarAnimationBehavior(myTrack.animator, myThumb.animator);
  }

  int getThickness() {
    return scale(myScrollBar == null || isOpaque(myScrollBar) ? myThickness : myThicknessMax);
  }

  int getMinimalThickness() {
    return scale(myScrollBar == null || isOpaque(myScrollBar) ? myThickness : myThicknessMin);
  }

  void toggle(boolean isOn) {
    myAnimationBehavior.onToggle(isOn);
  }

  static boolean isOpaque(Component c) {
    if (c.isOpaque()) return true;
    Container parent = c.getParent();
    // do not allow non-opaque scroll bars, because default layout does not support them
    return parent instanceof JScrollPane && parent.getLayout() instanceof ScrollPaneLayout.UIResource;
  }

  boolean isAbsolutePositioning(MouseEvent event) {
    return SwingUtilities.isMiddleMouseButton(event);
  }

  boolean isTrackClickable() {
    return isOpaque(myScrollBar) || myAnimationBehavior.getTrackFrame() > 0;
  }

  boolean isTrackExpandable() {
    return false;
  }

  boolean isTrackContains(int x, int y) {
    return myTrack.bounds.contains(x, y);
  }

  boolean isThumbContains(int x, int y) {
    return myThumb.bounds.contains(x, y);
  }

  void paintTrack(Graphics2D g, JComponent c) {
    paint(myTrack, g, c, false);
  }

  void paintThumb(Graphics2D g, JComponent c) {
    paint(myThumb, g, c, ScrollSettings.isThumbSmallIfOpaque() && isOpaque(c));
  }

  void paint(ScrollBarPainter p, Graphics2D g, JComponent c, boolean small) {
    int x = p.bounds.x;
    int y = p.bounds.y;
    int width = p.bounds.width;
    int height = p.bounds.height;

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

    Insets insets = getInsets(small);
    x += insets.left;
    y += insets.top;
    width -= (insets.left + insets.right);
    height -= (insets.top + insets.bottom);

    p.paint(g, x, y, width, height, p.animator.myValue);
  }

  protected @NotNull Insets getInsets(boolean small) {
    return small ? JBUI.insets(1) : JBUI.emptyInsets();
  }

  private int getTrackOffset(int offset) {
    if (!isTrackExpandable()) return offset;
    float value = myAnimationBehavior.getTrackFrame();
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
    value = JBUIScale.scale(value);
    return switch (UIUtil.getComponentStyle(myScrollBar)) {
      case LARGE -> (int)(value * 1.15);
      case SMALL -> (int)(value * 0.857);
      case MINI -> (int)(value * 0.714);
      case REGULAR -> value;
    };
  }

  @Override
  public void installUI(JComponent c) {
    myScrollBar = (JScrollBar)c;
    ScrollBarPainter.setBackground(c);
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
    myAnimationBehavior.onUninstall();
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
    Dimension preferred = new Dimension(thickness, thickness);
    if (alignment == Alignment.LEFT || alignment == Alignment.RIGHT) {
      preferred.height += preferred.height;
      addPreferredHeight(preferred, ComponentUtil.getClientProperty(myScrollBar, LEADING));
      addPreferredHeight(preferred, ComponentUtil.getClientProperty(myScrollBar, TRAILING));
    }
    else {
      preferred.width += preferred.width;
      addPreferredWidth(preferred, ComponentUtil.getClientProperty(myScrollBar, LEADING));
      addPreferredWidth(preferred, ComponentUtil.getClientProperty(myScrollBar, TRAILING));
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
      Color background = !isOpaque(c) ? null : c.getBackground();
      if (background != null) {
        g.setColor(background);
        g.fillRect(0, 0, c.getWidth(), c.getHeight());
      }
      Rectangle bounds = new Rectangle(c.getWidth(), c.getHeight());
      JBInsets.removeFrom(bounds, c.getInsets());
      // process an area before the track
      Component leading = ComponentUtil.getClientProperty(c, LEADING);
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
      Component trailing = ComponentUtil.getClientProperty(c, TRAILING);
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
      // do not set track size bigger that expected thickness
      if (alignment == Alignment.LEFT || alignment == Alignment.RIGHT) {
        int offset = bounds.width - getThickness();
        if (offset > 0) {
          bounds.width -= offset;
          if (alignment == Alignment.RIGHT) bounds.x += offset;
        }
      }
      else {
        int offset = bounds.height - getThickness();
        if (offset > 0) {
          bounds.height -= offset;
          if (alignment == Alignment.BOTTOM) bounds.y += offset;
        }
      }
      boolean animate = !myTrack.bounds.equals(bounds); // animate thumb on resize
      if (animate) myTrack.bounds.setBounds(bounds);
      updateThumbBounds(animate);
      paintTrack((Graphics2D)g, c);
      // process additional drawing on the track
      RegionPainter<Object> track = ComponentUtil.getClientProperty(c, JBScrollBar.TRACK);
      if (track != null && myTrack.bounds.width > 0 && myTrack.bounds.height > 0) {
        track.paint((Graphics2D)g, myTrack.bounds.x, myTrack.bounds.y, myTrack.bounds.width, myTrack.bounds.height, null);
      }
      // process drawing the thumb
      if (myThumb.bounds.width > 0 && myThumb.bounds.height > 0) {
        paintThumb((Graphics2D)g, c);
      }
    }
  }

  private void updateThumbBounds(boolean animate) {
    int value = 0;
    int min = myScrollBar.getMinimum();
    int max = myScrollBar.getMaximum();
    int range = max - min;
    if (range <= 0) {
      myThumb.bounds.setBounds(0, 0, 0, 0);
    }
    else if (VERTICAL == myScrollBar.getOrientation()) {
      int extent = myScrollBar.getVisibleAmount();
      int height = Math.max(convert(myTrack.bounds.height, extent, range), 2 * getThickness());
      if (myTrack.bounds.height <= height) {
        myThumb.bounds.setBounds(0, 0, 0, 0);
      }
      else {
        value = getValue();
        int maxY = myTrack.bounds.y + myTrack.bounds.height - height;
        int y = (value < max - extent) ? convert(myTrack.bounds.height - height, value - min, range - extent) : maxY;
        myThumb.bounds.setBounds(myTrack.bounds.x, adjust(y, myTrack.bounds.y, maxY), myTrack.bounds.width, height);
        animate |= myOldValue != value; // animate thumb on move
      }
    }
    else {
      int extent = myScrollBar.getVisibleAmount();
      int width = Math.max(convert(myTrack.bounds.width, extent, range), 2 * getThickness());
      if (myTrack.bounds.width <= width) {
        myThumb.bounds.setBounds(0, 0, 0, 0);
      }
      else {
        value = getValue();
        int maxX = myTrack.bounds.x + myTrack.bounds.width - width;
        int x = (value < max - extent) ? convert(myTrack.bounds.width - width, value - min, range - extent) : maxX;
        if (!myScrollBar.getComponentOrientation().isLeftToRight()) x = myTrack.bounds.x - x + maxX;
        myThumb.bounds.setBounds(adjust(x, myTrack.bounds.x, maxX), myTrack.bounds.y, width, myTrack.bounds.height);
        animate |= myOldValue != value; // animate thumb on move
      }
    }
    myOldValue = value;
    if (animate) myAnimationBehavior.onThumbMove();
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
    return Math.max(min, Math.min(value, max));
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
        if (!isOverTrack) myAnimationBehavior.onTrackHover(isOverTrack = true);
        boolean hover = isThumbContains(x, y);
        if (isOverThumb != hover) myAnimationBehavior.onThumbHover(isOverThumb = hover);
      }
      else {
        updateMouseExit();
      }
    }

    private void updateMouseExit() {
      if (isOverThumb) myAnimationBehavior.onThumbHover(isOverThumb = false);
      if (isOverTrack) myAnimationBehavior.onTrackHover(isOverTrack = false);
    }

    private boolean redispatchIfTrackNotClickable(MouseEvent event) {
      if (isTrackClickable()) return false;
      // redispatch current event to the view
      Container parent = myScrollBar.getParent();
      if (parent instanceof JScrollPane pane) {
        Component view = pane.getViewport().getView();
        if (view != null) {
          Point point = event.getLocationOnScreen();
          SwingUtilities.convertPointFromScreen(point, view);
          Component target = SwingUtilities.getDeepestComponentAt(view, point.x, point.y);
          MouseEventAdapter.redispatch(event, target);
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
        myOffset = vertical ? (myMouseY - myThumb.bounds.y) : (myMouseX - myThumb.bounds.x);
        isDragging = true;
      }
      else if (isTrackContains(myMouseX, myMouseY)) {
        // pressed on the track
        if (isAbsolutePositioning(event)) {
          myOffset = (vertical ? myThumb.bounds.height : myThumb.bounds.width) / 2;
          isDragging = true;
          setValueFrom(event);
        }
        else {
          myScrollTimer.stop();
          isDragging = false;
          if (VERTICAL == myScrollBar.getOrientation()) {
            int y = myThumb.bounds.isEmpty() ? myScrollBar.getHeight() / 2 : myThumb.bounds.y;
            isReversed = myMouseY < y;
          }
          else {
            int x = myThumb.bounds.isEmpty() ? myScrollBar.getWidth() / 2 : myThumb.bounds.x;
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
      if (myThumb.bounds.isEmpty() || SwingUtilities.isRightMouseButton(event)) return;
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
        if (!myThumb.bounds.isEmpty()) {
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
      updateThumbBounds(false);
      // TODO: update mouse
      isValueCached = false;
      repaint();
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
      String name = event.getPropertyName();
      if ("model".equals(name)) {
        BoundedRangeModel oldModel = (BoundedRangeModel)event.getOldValue();
        BoundedRangeModel newModel = (BoundedRangeModel)event.getNewValue();
        oldModel.removeChangeListener(this);
        newModel.addChangeListener(this);
      }
      if ("model".equals(name) || "orientation".equals(name) || "componentOrientation".equals(name)) {
        repaint();
      }
      if ("opaque".equals(name) || "visible".equals(name)) {
        myAnimationBehavior.onReset();
        myTrack.bounds.setBounds(0, 0, 0, 0);
        myThumb.bounds.setBounds(0, 0, 0, 0);
      }
    }

    private void setValueFrom(MouseEvent event) {
      int x = event.getX();
      int y = event.getY();

      int thumbMin, thumbMax, thumbPos;
      if (VERTICAL == myScrollBar.getOrientation()) {
        thumbMin = myTrack.bounds.y;
        thumbMax = myTrack.bounds.y + myTrack.bounds.height - myThumb.bounds.height;
        thumbPos = MathUtil.clamp(y - myOffset, thumbMin, thumbMax);
        if (myThumb.bounds.y != thumbPos) {
          int minY = Math.min(myThumb.bounds.y, thumbPos);
          int maxY = Math.max(myThumb.bounds.y, thumbPos) + myThumb.bounds.height;
          myThumb.bounds.y = thumbPos;
          myAnimationBehavior.onThumbMove();
          repaint(myThumb.bounds.x, minY, myThumb.bounds.width, maxY - minY);
        }
      }
      else {
        thumbMin = myTrack.bounds.x;
        thumbMax = myTrack.bounds.x + myTrack.bounds.width - myThumb.bounds.width;
        thumbPos = MathUtil.clamp(x - myOffset, thumbMin, thumbMax);
        if (myThumb.bounds.x != thumbPos) {
          int minX = Math.min(myThumb.bounds.x, thumbPos);
          int maxX = Math.max(myThumb.bounds.x, thumbPos) + myThumb.bounds.width;
          myThumb.bounds.x = thumbPos;
          myAnimationBehavior.onThumbMove();
          repaint(minX, myThumb.bounds.y, maxX - minX, myThumb.bounds.height);
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
      return myMouseY < myThumb.bounds.y;
    }

    private boolean isMouseOnLeft() {
      return myMouseX < myThumb.bounds.x;
    }

    private boolean isMouseOnRight() {
      return myMouseX > myThumb.bounds.x + myThumb.bounds.width;
    }

    private boolean isMouseOnBottom() {
      return myMouseY > myThumb.bounds.y + myThumb.bounds.height;
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
