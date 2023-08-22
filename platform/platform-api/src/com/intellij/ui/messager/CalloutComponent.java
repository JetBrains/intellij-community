// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.messager;


import com.intellij.ui.DrawUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.LineEndDecorator;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.paint.LinePainter2D;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Line2D;

/**
 * @author kir
 */
public class CalloutComponent {

  private static final int POINTER_LENGTH = 20;

  private final JDialog myFrame;

  private final JComponent myInnerComponent;

  private ComponentListener myComponentListener;
  private WindowListener myWindowListener;
  private WindowStateListener myWindowStateListener;
  private AWTEventListener myMulticastListener;
  private KeyEventDispatcher myKeyEventDispatcher;

  protected JComponent myTargetComponent;
  protected Window myTargetWindow;
  protected Pointer myPointerComponent;
  private final KeyboardFocusManager myKeyboardFocusManager;

  public CalloutComponent(JComponent component) {
    super();

    myKeyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    myInnerComponent = component;
    myInnerComponent.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

    myFrame = new JDialog();
    myFrame.setUndecorated(true);
    myFrame.setFocusable(false);
    myFrame.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    myFrame.setFocusableWindowState(false);

    myFrame.getContentPane().setLayout(new BorderLayout());
    myFrame.getContentPane().add(new Wrapper(myInnerComponent), BorderLayout.CENTER);
  }

  public void show(int location, final RelativePoint target) {
    myFrame.pack();
    Dimension frameSize = myFrame.getPreferredSize();

    final Point targetScreenPoint = target.getScreenPoint();
    Point framePoint = new Point();

    switch (location) {
      case Callout.NORTH_WEST -> {
        framePoint.x = targetScreenPoint.x - frameSize.width - getPointerShift();
        framePoint.y = targetScreenPoint.y - frameSize.height - getPointerShift();
      }
      case Callout.NORTH_EAST -> {
        framePoint.x = targetScreenPoint.x + getPointerShift();
        framePoint.y = targetScreenPoint.y - frameSize.height - getPointerShift();
      }
      case Callout.SOUTH_EAST -> {
        framePoint.x = targetScreenPoint.x + getPointerShift();
        framePoint.y = targetScreenPoint.y + getPointerShift();
      }
      case Callout.SOUTH_WEST -> {
        framePoint.x = targetScreenPoint.x - frameSize.width - getPointerShift();
        framePoint.y = targetScreenPoint.y + getPointerShift();
      }
    }

    myPointerComponent = new Pointer(location);

    final Rectangle frameBounds = new Rectangle(framePoint, frameSize);
    ScreenUtil.moveRectangleToFitTheScreen(frameBounds);

    myTargetComponent = (JComponent) target.getComponent();
    myTargetWindow = SwingUtilities.getWindowAncestor(myTargetComponent);

    final JLayeredPane layered = getLayeredPane(myTargetWindow);
    final Rectangle layeredBounds = new RelativeRectangle(layered).getScreenRectangle();

    final boolean[] outside = getOutsideAxisCodes(layeredBounds, frameBounds);
    if (outside != null) {
      boolean x = outside[0];
      boolean y = outside[1];

      switch (location) {
        case Callout.NORTH_WEST -> {
          if (x) {
            frameBounds.x = layeredBounds.x - frameBounds.width;
          }
          if (y) {
            frameBounds.y = layeredBounds.y - frameBounds.height;
          }
        }
        case Callout.NORTH_EAST -> {
          if (x) {
            frameBounds.x = (int)layeredBounds.getMaxX();
          }
          if (y) {
            frameBounds.y = layeredBounds.y - frameBounds.height;
          }
        }
        case Callout.SOUTH_EAST -> {
          if (x) {
            frameBounds.x = (int)layeredBounds.getMaxX();
          }
          if (y) {
            frameBounds.y = (int)layeredBounds.getMaxY();
          }
        }
        case Callout.SOUTH_WEST -> {
          if (x) {
            frameBounds.x = layeredBounds.x - frameBounds.width;
          }
          if (y) {
            frameBounds.y = (int)layeredBounds.getMaxY();
          }
        }
      }
    }


    Point targetLayeredPoint = target.getPoint(layered);
    Rectangle frameLayeredBounds = RelativeRectangle.fromScreen(layered, frameBounds).getRectangleOn(layered);

    Rectangle pointerBounds = new Rectangle();
    final int extraPoint = 1;
    switch (location) {
      case Callout.NORTH_WEST -> {
        pointerBounds.x = (int)frameLayeredBounds.getMaxX() - extraPoint;
        pointerBounds.y = (int)frameLayeredBounds.getMaxY() - extraPoint;
        pointerBounds.width = targetLayeredPoint.x - pointerBounds.x;
        pointerBounds.height = targetLayeredPoint.y - pointerBounds.y;
      }
      case Callout.NORTH_EAST -> {
        pointerBounds.x = targetLayeredPoint.x;
        pointerBounds.y = (int)frameLayeredBounds.getMaxY() - extraPoint;
        pointerBounds.width = frameLayeredBounds.x + extraPoint - targetLayeredPoint.x;
        pointerBounds.height = targetLayeredPoint.y - pointerBounds.y;
      }
      case Callout.SOUTH_EAST -> {
        pointerBounds.x = targetLayeredPoint.x;
        pointerBounds.y = targetLayeredPoint.y;
        pointerBounds.width = frameLayeredBounds.x + extraPoint - targetLayeredPoint.x;
        pointerBounds.height = (int)frameLayeredBounds.getMaxY() + extraPoint - targetLayeredPoint.y - frameLayeredBounds.height;
      }
      case Callout.SOUTH_WEST -> {
        pointerBounds.x = (int)frameLayeredBounds.getMaxX() - extraPoint;
        pointerBounds.y = targetLayeredPoint.y;
        pointerBounds.width = targetLayeredPoint.x - pointerBounds.x;
        pointerBounds.height = frameLayeredBounds.y + extraPoint - targetLayeredPoint.y;
      }
    }

    layered.add(myPointerComponent, JLayeredPane.POPUP_LAYER);
    myPointerComponent.setBounds(pointerBounds);

    myFrame.setBounds(frameBounds);
    myFrame.setVisible(true);

    SwingUtilities.invokeLater(() -> {
      installDisposeListeners();
      myFrame.setVisible(true);
    });
  }

  private boolean[] getOutsideAxisCodes(final Rectangle layeredBounds, final Rectangle frameBounds) {
    boolean x = frameBounds.getMaxX() < layeredBounds.x || layeredBounds.getMaxX() < frameBounds.x;
    boolean y = frameBounds.getMaxY() < layeredBounds.y || layeredBounds.getMaxY() < frameBounds.y;

    if (x || y) {
      return new boolean[] {x, y};
    } else {
      return null;
    }
  }

  private void installDisposeListeners() {
    myKeyEventDispatcher = e -> {
      dispose();
      return false;
    };
    myKeyboardFocusManager.addKeyEventDispatcher(myKeyEventDispatcher);

    myMulticastListener = new AWTEventListener() {
      @Override
      public void eventDispatched(AWTEvent event) {
        if (event.getID() == MouseEvent.MOUSE_PRESSED) {
          dispose();
        }
      }
    };

    Toolkit.getDefaultToolkit().addAWTEventListener(myMulticastListener, AWTEvent.MOUSE_EVENT_MASK );

    myComponentListener = new ComponentListener() {
      @Override
      public void componentHidden(ComponentEvent e) {
        dispose();
      }

      @Override
      public void componentMoved(ComponentEvent e) {
        dispose();
      }

      @Override
      public void componentResized(ComponentEvent e) {
        dispose();
      }

      @Override
      public void componentShown(ComponentEvent e) {
        dispose();
      }
    };

    myWindowListener = new WindowListener() {
      @Override
      public void windowActivated(WindowEvent e) {
        dispose();
      }

      @Override
      public void windowClosed(WindowEvent e) {
        dispose();
      }

      @Override
      public void windowClosing(WindowEvent e) {
        dispose();
      }

      @Override
      public void windowDeactivated(WindowEvent e) {
        dispose();
      }

      @Override
      public void windowDeiconified(WindowEvent e) {
        dispose();
      }

      @Override
      public void windowIconified(WindowEvent e) {
        dispose();
      }

      @Override
      public void windowOpened(WindowEvent e) {
        dispose();
      }
    };
    myTargetWindow.addWindowListener(myWindowListener);

    myWindowStateListener = new WindowStateListener() {
      @Override
      public void windowStateChanged(WindowEvent e) {
        dispose();
      }
    };
    myTargetWindow.addWindowStateListener(myWindowStateListener);
  }

  private void dispose() {

    Runnable runnable = () -> {
      myFrame.dispose();

      Toolkit.getDefaultToolkit().removeAWTEventListener(myMulticastListener);
      myKeyboardFocusManager.removeKeyEventDispatcher(myKeyEventDispatcher);

      myTargetComponent.removeComponentListener(myComponentListener);
      myTargetWindow.removeWindowListener(myWindowListener);
      myTargetWindow.removeWindowStateListener(myWindowStateListener);

      final Container parent = myPointerComponent.getParent();
      final Rectangle bounds = myPointerComponent.getBounds();
      if (parent != null) {
        parent.remove(myPointerComponent);
        parent.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
      }
    };

    SwingUtilities.invokeLater(runnable);
  }

  private int getPointerShift() {
    return (int) Math.sqrt(POINTER_LENGTH * POINTER_LENGTH / 2.0);
  }

  private Color getFillColor() {
    return JBColor.background();
  }

  private Color getBoundsColor() {
    return JBColor.RED;
  }

  private class Wrapper extends NonOpaquePanel {

    Wrapper(JComponent component) {
      setBorder(BorderFactory.createEmptyBorder(2, 3, 2, 3));
      setLayout(new BorderLayout());
      add(component, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g;

      final Object old = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      g.setColor(getFillColor());
      g.fillRect(1, 1, getWidth() - 2, getHeight() - 2);

      DrawUtil.drawRoundRect(g, 0, 0, getWidth() - 1, getHeight() - 1, getBoundsColor());

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old);
    }
  }

  private class Pointer extends NonOpaquePanel {
    private final int myOrientation;

    Pointer(int orientation) {
      myOrientation = orientation;
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g;

      final Object old = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      g2.setColor(getBoundsColor());

      Line2D line = new Line2D.Double();
      switch (myOrientation) {
        case Callout.NORTH_WEST -> line.setLine(0, 0, getWidth() - 1, getHeight() - 1);
        case Callout.NORTH_EAST -> line.setLine(getWidth() - 1, 0, 0, getHeight() - 1);
        case Callout.SOUTH_EAST -> line.setLine(getWidth() - 1, getHeight() - 1, 0, 0);
        case Callout.SOUTH_WEST -> line.setLine(0, getHeight() - 1, getWidth() - 1, 0);
      }

      LinePainter2D.paint(g2, (int)line.getX1(), (int)line.getY1(), (int)line.getX2(), (int)line.getY2());

      final Shape arrow = LineEndDecorator.getArrowShape(line, line.getP2());
      g2.fill(arrow);

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old);
    }
  }

  private static JLayeredPane getLayeredPane(Window window) {
    if (window instanceof JFrame) {
      return ((JFrame) window).getRootPane().getLayeredPane();
    } else if (window instanceof JDialog) {
      return ((JDialog) window).getRootPane().getLayeredPane();
    }

    return null;
  }

}
