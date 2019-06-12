// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Weighted;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

/**
 * @author Konstantin Bulenkov
 */
public class OnePixelDivider extends Divider {
  public static final Color BACKGROUND = new JBColor(() -> {
    final Color bg = UIManager.getColor("OnePixelDivider.background");
    return bg != null ? bg : JBColor.border();
  });

  private boolean myVertical;
  private final Splittable mySplitter;
  private boolean myResizeEnabled;
  private boolean mySwitchOrientationEnabled;
  protected Point myPoint;
  private IdeGlassPane myGlassPane;
  private final MouseAdapter myListener = new MyMouseAdapter();
  private Disposable myDisposable;

  public OnePixelDivider(boolean vertical, Splittable splitter) {
    super(new GridBagLayout());
    mySplitter = splitter;
    myResizeEnabled = true;
    mySwitchOrientationEnabled = false;
    setFocusable(false);
    enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    setOrientation(vertical);
    setBackground(BACKGROUND);
  }

  @Override
  public void paint(Graphics g) {
    final Rectangle bounds = g.getClipBounds();
    if (mySplitter instanceof OnePixelSplitter) {
      final Supplier<Insets> blindZone = ((OnePixelSplitter)mySplitter).getBlindZone();
      if (blindZone != null) {
        final Insets insets = blindZone.get();
        if (insets != null) {
          bounds.x += insets.left;
          bounds.y += insets.top;
          bounds.width -= insets.left + insets.right;
          bounds.height -= insets.top + insets.bottom;
          g.setColor(getBackground());
          g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
          return;
        }
      }
    }
    super.paint(g);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    init();
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (myDisposable != null && !Disposer.isDisposed(myDisposable)) {
      Disposer.dispose(myDisposable);
    }
  }

  private boolean myDragging = false;

  private void setDragging(boolean dragging) {
    if (myDragging != dragging) {
      myDragging = dragging;
      mySplitter.setDragging(dragging);
    }
  }
  private class MyMouseAdapter extends MouseAdapter implements Weighted {
    @Override
    public void mousePressed(MouseEvent e) {
      setDragging(isInDragZone(e));
      _processMouseEvent(e);
      if (myDragging) {
        e.consume();
      }
    }

    boolean isInDragZone(MouseEvent e) {
      MouseEvent event = getTargetEvent(e);
      Point p = event.getPoint();
      boolean vertical = isVertical();
      OnePixelDivider d = OnePixelDivider.this;
      if ((vertical ? p.x : p.y) < 0 || vertical && p.x > d.getWidth() || !vertical && p.y > d.getHeight()) return false;
      int r = Math.abs(vertical ? p.y : p.x);
      return r < JBUIScale.scale(Registry.intValue("ide.splitter.mouseZone"));
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      _processMouseEvent(e);
      if (myDragging) {
        e.consume();
      }
      setDragging(false);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      final OnePixelDivider divider = OnePixelDivider.this;
      if (isInDragZone(e)) {
        myGlassPane.setCursor(divider.getCursor(), divider);
      } else {
        myGlassPane.setCursor(null, divider);
      }
      _processMouseMotionEvent(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      _processMouseMotionEvent(e);
    }
    @Override
    public double getWeight() {
      return 1;
    }
    private void _processMouseMotionEvent(MouseEvent e) {
      MouseEvent event = getTargetEvent(e);
      if (event == null) {
        myGlassPane.setCursor(null, myListener);
        return;
      }

      processMouseMotionEvent(event);
      if (event.isConsumed()) {
        e.consume();
      }
    }

    private void _processMouseEvent(MouseEvent e) {
      MouseEvent event = getTargetEvent(e);
      if (event == null) {
        myGlassPane.setCursor(null, myListener);
        return;
      }

      processMouseEvent(event);
      if (event.isConsumed()) {
        e.consume();
      }
    }
  }

  private MouseEvent getTargetEvent(MouseEvent e) {
    return SwingUtilities.convertMouseEvent(e.getComponent(), e, this);
  }

  private void init() {
    myGlassPane = IdeGlassPaneUtil.find(this);
    myDisposable = Disposer.newDisposable();
    myGlassPane.addMouseMotionPreprocessor(myListener, myDisposable);
    myGlassPane.addMousePreprocessor(myListener, myDisposable);
  }

  @Override
  public void setOrientation(boolean vertical) {
    removeAll();
    myVertical = vertical;
    final int cursorType = isVertical() ? Cursor.N_RESIZE_CURSOR : Cursor.W_RESIZE_CURSOR;
    UIUtil.setCursor(this, Cursor.getPredefinedCursor(cursorType));
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    super.processMouseMotionEvent(e);
    if (!myResizeEnabled) return;
    if (MouseEvent.MOUSE_DRAGGED == e.getID() && myDragging) {
      myPoint = SwingUtilities.convertPoint(this, e.getPoint(), mySplitter.asComponent());
      float proportion;
      final float firstMinProportion = mySplitter.getMinProportion(true);
      final float secondMinProportion = mySplitter.getMinProportion(false);
      if (isVertical()) {
        if (getHeight() > 0) {
          proportion = Math.min(1.0f, Math
            .max(.0f, Math.min(Math.max(firstMinProportion, (float)myPoint.y / (float)mySplitter.asComponent().getHeight()), 1 - secondMinProportion)));
          mySplitter.setProportion(proportion);
        }
      }
      else {
        if (getWidth() > 0) {
          proportion = Math.min(1.0f, Math.max(.0f, Math.min(
            Math.max(firstMinProportion, (float)myPoint.x / (float)mySplitter.asComponent().getWidth()), 1 - secondMinProportion)));
          mySplitter.setProportion(proportion);
        }
      }
      e.consume();
    }
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    super.processMouseEvent(e);
    if (e.getID() == MouseEvent.MOUSE_CLICKED) {
      if (mySwitchOrientationEnabled
          && e.getClickCount() == 1
          && SwingUtilities.isLeftMouseButton(e) && (SystemInfo.isMac ? e.isMetaDown() : e.isControlDown())) {
        mySplitter.setOrientation(!mySplitter.getOrientation());
      }
      if (myResizeEnabled && e.getClickCount() == 2) {
        mySplitter.setProportion(.5f);
      }
    }
  }

  @Override
  public void setResizeEnabled(boolean resizeEnabled) {
    myResizeEnabled = resizeEnabled;
    if (!myResizeEnabled) {
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }
    else {
      setCursor(isVertical() ?
                Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR) :
                Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
    }
  }

  @Override
  public void setSwitchOrientationEnabled(boolean switchOrientationEnabled) {
    mySwitchOrientationEnabled = switchOrientationEnabled;
  }


  public boolean isVertical() {
    return myVertical;
  }
}
