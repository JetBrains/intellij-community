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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.Gray;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import javax.swing.event.MouseInputListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicRootPaneUI;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaRootPaneUI extends BasicRootPaneUI {
  private Cursor myLastCursor;

  private enum CursorState {EXITED, ENTERED, NIL}

  private static final int CORNER_DRAG_WIDTH = 16;

  private static final int BORDER_DRAG_THICKNESS = 5;

  private Window myWindow;

  private JComponent myTitlePane;

  private MouseInputListener myMouseInputListener;

  private MouseInputListener myTitleMouseInputListener;

  private LayoutManager myLayoutManager;

  private LayoutManager myOldLayout;

  protected JRootPane myRootPane;

  protected WindowListener myWindowListener;

  protected Window myCurrentWindow;

  protected HierarchyListener myHierarchyListener;

  protected ComponentListener myWindowComponentListener;

  protected GraphicsConfiguration currentRootPaneGC;

  protected PropertyChangeListener myPropertyChangeListener;

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent comp) {
    return isCustomDecoration() ? new DarculaRootPaneUI() : createDefaultWindowsRootPaneUI();
  }

  private static ComponentUI createDefaultWindowsRootPaneUI() {
    try {
      return (ComponentUI)Class.forName("com.sun.java.swing.plaf.windows.WindowsRootPaneUI").newInstance();
    } catch (Exception e) {
      return new BasicRootPaneUI();
    }
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);

    if (isCustomDecoration()) {
      myRootPane = (JRootPane)c;
      int style = myRootPane.getWindowDecorationStyle();
      if (style != JRootPane.NONE) {
        installClientDecorations(myRootPane);
      }
    }
  }

  public void installMenuBar(JMenuBar menu) {
    if (menu != null && isCustomDecoration()) {
      getTitlePane().add(menu);
    }
  }

  @Override
  public void uninstallUI(JComponent c) {
    super.uninstallUI(c);

    if (isCustomDecoration()) {
      uninstallClientDecorations(myRootPane);

      myLayoutManager = null;
      myMouseInputListener = null;

      myRootPane = null;
    }
  }

  private static boolean isCustomDecoration() {
    return Registry.is("ide.win.frame.decoration");
  }

  public void installBorder(JRootPane root) {
    int style = root.getWindowDecorationStyle();

    if (style == JRootPane.NONE) {
      LookAndFeel.uninstallBorder(root);
    }
    else {
      root.setBorder(JBUI.Borders.customLine(Gray._73, 1, 1, 1, 1));
      //LookAndFeel.installBorder(root, "RootPane.border");
    }
  }

  private static void uninstallBorder(JRootPane root) {
    LookAndFeel.uninstallBorder(root);
  }

  private void installWindowListeners(JRootPane root, Component parent) {
    myWindow = parent == null ? null : UIUtil.getWindow(parent);

    if (myWindow != null) {
      if (myMouseInputListener == null) {
        myMouseInputListener = createWindowMouseInputListener(root);
      }
      myWindow.addMouseListener(myMouseInputListener);
      myWindow.addMouseMotionListener(myMouseInputListener);

      if (myTitlePane != null) {
        if (myTitleMouseInputListener == null) {
          myTitleMouseInputListener = new TitleMouseInputHandler();
        }
        myTitlePane
          .addMouseMotionListener(myTitleMouseInputListener);
        myTitlePane
          .addMouseListener(myTitleMouseInputListener);
      }
      setMaximized();
    }
  }

  private void uninstallWindowListeners(JRootPane root) {
    if (myWindow != null) {
      myWindow.removeMouseListener(myMouseInputListener);
      myWindow.removeMouseMotionListener(myMouseInputListener);
    }
    if (myTitlePane != null) {
      myTitlePane.removeMouseListener(myTitleMouseInputListener);
      myTitlePane.removeMouseMotionListener(myTitleMouseInputListener);
    }
  }

  private void installLayout(JRootPane root) {
    if (myLayoutManager == null) {
      myLayoutManager = createLayoutManager();
    }
    myOldLayout = root.getLayout();
    root.setLayout(myLayoutManager);
  }

  @Override
  protected void installListeners(final JRootPane root) {
    super.installListeners(root);

    myHierarchyListener = new HierarchyListener() {
      public void hierarchyChanged(HierarchyEvent e) {
        Component parent = root.getParent();
        if (parent == null) {
          return;
        }
        if (parent.getClass().getName().startsWith("org.jdesktop.jdic.tray")
            || (parent.getClass().getName().compareTo("javax.swing.Popup$HeavyWeightWindow") == 0)) {

          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              root.removeHierarchyListener(myHierarchyListener);
              myHierarchyListener = null;
            }
          });
        }

        Window currWindow = UIUtil.getWindow(parent);
        if (myWindowListener != null) {
          myCurrentWindow
            .removeWindowListener(myWindowListener);
          myWindowListener = null;
        }
        if (myWindowComponentListener != null) {
          myCurrentWindow
            .removeComponentListener(myWindowComponentListener);
          myWindowComponentListener = null;
        }
        if (currWindow != null) {
          myWindowListener = new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
              //noinspection SSBasedInspection
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  Frame[] frames = Frame.getFrames();
                  for (Frame frame : frames) {
                    if (frame.isDisplayable()) {
                      return;
                    }
                  }
                }
              });
            }
          };

          if (!(parent instanceof JInternalFrame)) {
            currWindow.addWindowListener(myWindowListener);
          }

          myWindowComponentListener = new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
              processNewPosition();
            }

            @Override
            public void componentResized(ComponentEvent e) {
              processNewPosition();
            }

            private void processNewPosition() {
              //noinspection SSBasedInspection
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  if (myWindow == null) {
                    return;
                  }

                  if (!myWindow.isShowing()
                      || !myWindow.isDisplayable()) {
                    currentRootPaneGC = null;
                    return;
                  }

                  GraphicsEnvironment ge = GraphicsEnvironment
                    .getLocalGraphicsEnvironment();
                  GraphicsDevice[] gds = ge
                    .getScreenDevices();
                  if (gds.length == 1) {
                    return;
                  }
                  Point midLoc = new Point(myWindow.getLocationOnScreen().x + myWindow.getWidth() / 2,
                                           myWindow.getLocationOnScreen().y + myWindow.getHeight() / 2);

                  for (GraphicsDevice gd : gds) {
                    GraphicsConfiguration gc = gd.getDefaultConfiguration();
                    Rectangle bounds = gc.getBounds();
                    if (bounds.contains(midLoc)) {
                      if (gc != currentRootPaneGC) {
                        currentRootPaneGC = gc;
                        setMaximized();
                      }
                      break;
                    }
                  }
                }
              });
            }
          };

          if (parent instanceof JFrame) {
            currWindow.addComponentListener(myWindowComponentListener);
          }

          myWindow = currWindow;
        }
        myCurrentWindow = currWindow;
      }
    };
    root.addHierarchyListener(myHierarchyListener);
    root.addPropertyChangeListener(myPropertyChangeListener);
  }

  @Override
  protected void uninstallListeners(JRootPane root) {
    if (myWindow != null) {
      myWindow.removeWindowListener(myWindowListener);
      myWindowListener = null;
      myWindow
        .removeComponentListener(myWindowComponentListener);
      myWindowComponentListener = null;
    }
    root.removeHierarchyListener(myHierarchyListener);
    myHierarchyListener = null;

    root.removePropertyChangeListener(myPropertyChangeListener);
    myPropertyChangeListener = null;

    super.uninstallListeners(root);
  }

  /**
   * Uninstalls the previously installed <code>LayoutManager</code>.
   *
   * @param root Root pane.
   */
  private void uninstallLayout(JRootPane root) {
    if (myOldLayout != null) {
      root.setLayout(myOldLayout);
      myOldLayout = null;
    }
  }

  /**
   * Installs the necessary state onto the JRootPane to render client
   * decorations. This is ONLY invoked if the <code>JRootPane</code> has a
   * decoration style other than <code>JRootPane.NONE</code>.
   *
   * @param root Root pane.
   */
  private void installClientDecorations(JRootPane root) {
    installBorder(root);

    JComponent titlePane = createTitlePane(root);

    setTitlePane(root, titlePane);
    installWindowListeners(root, root.getParent());
    installLayout(root);
    if (myWindow != null) {
      root.revalidate();
      root.repaint();
    }
  }

  private void uninstallClientDecorations(JRootPane root) {
    uninstallBorder(root);
    uninstallWindowListeners(root);
    setTitlePane(root, null);
    uninstallLayout(root);
    int style = root.getWindowDecorationStyle();
    if (style == JRootPane.NONE) {
      root.repaint();
      root.revalidate();
    }

    if (myWindow != null) {
      myWindow.setCursor(Cursor
                                .getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }
    myWindow = null;
  }

  protected JComponent createTitlePane(JRootPane root) {
    return new DarculaTitlePane(root, this);
  }

  private MouseInputListener createWindowMouseInputListener(JRootPane root) {
    return new MouseInputHandler();
  }

  protected LayoutManager createLayoutManager() {
    return new DarculaRootLayout();
  }

  private void setTitlePane(JRootPane root, JComponent titlePane) {
    JLayeredPane layeredPane = root.getLayeredPane();
    JComponent oldTitlePane = getTitlePane();

    if (oldTitlePane != null) {
      layeredPane.remove(oldTitlePane);
    }
    if (titlePane != null) {
      layeredPane.add(titlePane, JLayeredPane.FRAME_CONTENT_LAYER);
      titlePane.setVisible(true);
    }
    myTitlePane = titlePane;
  }

  public void setMaximized() {
    if (Registry.is("darcula.fix.maximized.frame.bounds")) return;

    Component tla = myRootPane.getTopLevelAncestor();
    GraphicsConfiguration gc = (currentRootPaneGC != null) ? currentRootPaneGC
                                                           : tla.getGraphicsConfiguration();
    Rectangle screenBounds = gc.getBounds();
    screenBounds.x = 0;
    screenBounds.y = 0;
    Insets screenInsets = ScreenUtil.getScreenInsets(gc);
    Rectangle maxBounds = new Rectangle(
      (screenBounds.x + screenInsets.left),
      (screenBounds.y + screenInsets.top), screenBounds.width
                                           - ((screenInsets.left + screenInsets.right)),
      screenBounds.height
      - ((screenInsets.top + screenInsets.bottom)));
    if (tla instanceof JFrame) {
      ((JFrame)tla).setMaximizedBounds(maxBounds);
    }
  }

  public JComponent getTitlePane() {
    return myTitlePane;
  }

  protected JRootPane getRootPane() {
    return myRootPane;
  }

  @Override
  public void propertyChange(PropertyChangeEvent e) {
    super.propertyChange(e);

    String propertyName = e.getPropertyName();
    if (propertyName == null) {
      return;
    }

    if (propertyName.equals("windowDecorationStyle")) {
      JRootPane root = (JRootPane)e.getSource();
      int style = root.getWindowDecorationStyle();

      uninstallClientDecorations(root);
      if (style != JRootPane.NONE) {
        installClientDecorations(root);
      }
    }
    if (propertyName.equals("ancestor")) {
      uninstallWindowListeners(myRootPane);
      if (((JRootPane)e.getSource()).getWindowDecorationStyle() != JRootPane.NONE) {
        installWindowListeners(myRootPane, myRootPane.getParent());
      }
    }
  }

  protected class DarculaRootLayout implements LayoutManager2 {
    public Dimension preferredLayoutSize(Container parent) {
      Dimension cpd, mbd, tpd;
      int cpWidth = 0;
      int cpHeight = 0;
      int mbWidth = 0;
      int mbHeight = 0;
      int tpWidth = 0;
      int tpHeight = 0;
      Insets i = parent.getInsets();
      JRootPane root = (JRootPane)parent;

      if (root.getContentPane() != null) {
        cpd = root.getContentPane().getPreferredSize();
      }
      else {
        cpd = root.getSize();
      }
      if (cpd != null) {
        cpWidth = cpd.width;
        cpHeight = cpd.height;
      }

      if (root.getJMenuBar() != null) {
        mbd = root.getJMenuBar().getPreferredSize();
        if (mbd != null) {
          mbWidth = mbd.width;
          mbHeight = mbd.height;
        }
      }

      if ((root.getWindowDecorationStyle() != JRootPane.NONE)
          && (root.getUI() instanceof DarculaRootPaneUI)) {
        JComponent titlePane = ((DarculaRootPaneUI)root.getUI()).getTitlePane();
        if (titlePane != null) {
          tpd = titlePane.getPreferredSize();
          if (tpd != null) {
            tpWidth = tpd.width;
            tpHeight = tpd.height;
          }
        }
      }

      return new Dimension(Math.max(Math.max(cpWidth, mbWidth), tpWidth)
                           + i.left + i.right, cpHeight + mbHeight + tpHeight + i.top
                                               + i.bottom);
    }

    public Dimension minimumLayoutSize(Container parent) {
      Dimension cpd, mbd, tpd;
      int cpWidth = 0;
      int cpHeight = 0;
      int mbWidth = 0;
      int mbHeight = 0;
      int tpWidth = 0;
      int tpHeight = 0;
      Insets i = parent.getInsets();
      JRootPane root = (JRootPane)parent;

      if (root.getContentPane() != null) {
        cpd = root.getContentPane().getMinimumSize();
      }
      else {
        cpd = root.getSize();
      }
      if (cpd != null) {
        cpWidth = cpd.width;
        cpHeight = cpd.height;
      }

      if (root.getJMenuBar() != null) {
        mbd = root.getJMenuBar().getMinimumSize();
        if (mbd != null) {
          mbWidth = mbd.width;
          mbHeight = mbd.height;
        }
      }
      if ((root.getWindowDecorationStyle() != JRootPane.NONE)
          && (root.getUI() instanceof DarculaRootPaneUI)) {
        JComponent titlePane = ((DarculaRootPaneUI)root.getUI())
          .getTitlePane();
        if (titlePane != null) {
          tpd = titlePane.getMinimumSize();
          if (tpd != null) {
            tpWidth = tpd.width;
            tpHeight = tpd.height;
          }
        }
      }

      return new Dimension(Math.max(Math.max(cpWidth, mbWidth), tpWidth)
                           + i.left + i.right, cpHeight + mbHeight + tpHeight + i.top
                                               + i.bottom);
    }

    public Dimension maximumLayoutSize(Container target) {
      Dimension cpd, mbd, tpd;
      int cpWidth = Integer.MAX_VALUE;
      int cpHeight = Integer.MAX_VALUE;
      int mbWidth = Integer.MAX_VALUE;
      int mbHeight = Integer.MAX_VALUE;
      int tpWidth = Integer.MAX_VALUE;
      int tpHeight = Integer.MAX_VALUE;
      Insets i = target.getInsets();
      JRootPane root = (JRootPane)target;

      if (root.getContentPane() != null) {
        cpd = root.getContentPane().getMaximumSize();
        if (cpd != null) {
          cpWidth = cpd.width;
          cpHeight = cpd.height;
        }
      }

      if (root.getJMenuBar() != null) {
        mbd = root.getJMenuBar().getMaximumSize();
        if (mbd != null) {
          mbWidth = mbd.width;
          mbHeight = mbd.height;
        }
      }

      if ((root.getWindowDecorationStyle() != JRootPane.NONE)
          && (root.getUI() instanceof DarculaRootPaneUI)) {
        JComponent titlePane = ((DarculaRootPaneUI)root.getUI())
          .getTitlePane();
        if (titlePane != null) {
          tpd = titlePane.getMaximumSize();
          if (tpd != null) {
            tpWidth = tpd.width;
            tpHeight = tpd.height;
          }
        }
      }

      int maxHeight = Math.max(Math.max(cpHeight, mbHeight), tpHeight);
      if (maxHeight != Integer.MAX_VALUE) {
        maxHeight = cpHeight + mbHeight + tpHeight + i.top + i.bottom;
      }

      int maxWidth = Math.max(Math.max(cpWidth, mbWidth), tpWidth);

      if (maxWidth != Integer.MAX_VALUE) {
        maxWidth += i.left + i.right;
      }

      return new Dimension(maxWidth, maxHeight);
    }

    public void layoutContainer(Container parent) {
      JRootPane root = (JRootPane)parent;
      Rectangle b = root.getBounds();
      Insets i = root.getInsets();
      int nextY = 0;
      int w = b.width - i.right - i.left;
      int h = b.height - i.top - i.bottom;

      if (root.getLayeredPane() != null) {
        root.getLayeredPane().setBounds(i.left, i.top, w, h);
      }
      if (root.getGlassPane() != null) {
        root.getGlassPane().setBounds(i.left, i.top, w, h);
      }

      if ((root.getWindowDecorationStyle() != JRootPane.NONE)
          && (root.getUI() instanceof DarculaRootPaneUI)) {
        JComponent titlePane = ((DarculaRootPaneUI)root.getUI())
          .getTitlePane();
        if (titlePane != null) {
          Dimension tpd = titlePane.getPreferredSize();
          if (tpd != null) {
            int tpHeight = tpd.height;
            titlePane.setBounds(0, 0, w, tpHeight);
            nextY += tpHeight;
          }
        }
      }
      if (root.getJMenuBar() != null) {
        Dimension mbd = root.getJMenuBar().getPreferredSize();
        root.getJMenuBar().setBounds(0, nextY, w, mbd.height);
        nextY += mbd.height;
      }
      if (root.getContentPane() != null) {

        root.getContentPane().setBounds(0, nextY, w, h < nextY ? 0 : h - nextY);
      }
    }

    public void addLayoutComponent(String name, Component comp) {
    }

    public void removeLayoutComponent(Component comp) {
    }

    public void addLayoutComponent(Component comp, Object constraints) {
    }

    public float getLayoutAlignmentX(Container target) {
      return 0.0f;
    }

    public float getLayoutAlignmentY(Container target) {
      return 0.0f;
    }

    public void invalidateLayout(Container target) {
    }
  }

  private static final int[] cursorMapping = new int[]{
    Cursor.NW_RESIZE_CURSOR, Cursor.NW_RESIZE_CURSOR,
    Cursor.N_RESIZE_CURSOR, Cursor.NE_RESIZE_CURSOR,
    Cursor.NE_RESIZE_CURSOR, Cursor.NW_RESIZE_CURSOR, 0, 0, 0,
    Cursor.NE_RESIZE_CURSOR, Cursor.W_RESIZE_CURSOR, 0, 0, 0,
    Cursor.E_RESIZE_CURSOR, Cursor.SW_RESIZE_CURSOR, 0, 0, 0,
    Cursor.SE_RESIZE_CURSOR, Cursor.SW_RESIZE_CURSOR,
    Cursor.SW_RESIZE_CURSOR, Cursor.S_RESIZE_CURSOR,
    Cursor.SE_RESIZE_CURSOR, Cursor.SE_RESIZE_CURSOR};

  private class MouseInputHandler implements MouseInputListener {
    private boolean isMovingWindow;
    private int dragCursor;
    private int dragOffsetX;
    private int dragOffsetY;
    private int dragWidth;
    private int dragHeight;

    @SuppressWarnings("unchecked")
    private final PrivilegedExceptionAction getLocationAction = new PrivilegedExceptionAction() {
      public Object run() throws HeadlessException {
        return MouseInfo.getPointerInfo().getLocation();
      }
    };

    public void mousePressed(MouseEvent ev) {
      JRootPane rootPane = getRootPane();

      if (rootPane.getWindowDecorationStyle() == JRootPane.NONE) {
        return;
      }
      Point dragWindowOffset = ev.getPoint();
      Window w = (Window)ev.getSource();
      if (w != null) {
        w.toFront();
      }
      Point convertedDragWindowOffset = SwingUtilities.convertPoint(w, dragWindowOffset, getTitlePane());

      Frame f = null;
      Dialog d = null;

      if (w instanceof Frame) {
        f = (Frame)w;
      }
      else if (w instanceof Dialog) {
        d = (Dialog)w;
      }

      int frameState = (f != null) ? f.getExtendedState() : 0;

      if ((getTitlePane() != null)
          && getTitlePane().contains(
        convertedDragWindowOffset)) {
        if ((((f != null) && ((frameState & Frame.MAXIMIZED_BOTH) == 0)) || (d != null))
            && (dragWindowOffset.y >= BORDER_DRAG_THICKNESS)
            && (dragWindowOffset.x >= BORDER_DRAG_THICKNESS)
            && (dragWindowOffset.x < w.getWidth() - BORDER_DRAG_THICKNESS)) {
          isMovingWindow = true;
          dragOffsetX = dragWindowOffset.x;
          dragOffsetY = dragWindowOffset.y;
        }
      }
      else if (((f != null) && f.isResizable() && ((frameState & Frame.MAXIMIZED_BOTH) == 0))
               || ((d != null) && d.isResizable())) {
        dragOffsetX = dragWindowOffset.x;
        dragOffsetY = dragWindowOffset.y;
        dragWidth = w.getWidth();
        dragHeight = w.getHeight();
        dragCursor = getCursor(calculateCorner(w,
                                                              dragWindowOffset.x, dragWindowOffset.y));
      }
    }

    public void mouseReleased(MouseEvent ev) {
      if ((dragCursor != 0)
          && (myWindow != null)
          && !myWindow.isValid()) {
        myWindow.validate();
        getRootPane().repaint();
      }
      isMovingWindow = false;
      dragCursor = 0;
    }

    public void mouseMoved(MouseEvent ev) {
      JRootPane root = getRootPane();

      if (root.getWindowDecorationStyle() == JRootPane.NONE) {
        return;
      }

      Window w = (Window)ev.getSource();

      Frame f = null;
      Dialog d = null;

      if (w instanceof Frame) {
        f = (Frame)w;
      }
      else if (w instanceof Dialog) {
        d = (Dialog)w;
      }

      int cursor = getCursor(calculateCorner(w, ev.getX(), ev.getY()));

      if ((cursor != 0)
          && (((f != null) && (f.isResizable() && ((f.getExtendedState() & Frame.MAXIMIZED_BOTH) == 0)))
              || ((d != null) && d.isResizable()))) {
        w.setCursor(Cursor.getPredefinedCursor(cursor));
      }
      else {
        w.setCursor(myLastCursor);
      }
    }

    private void adjust(Rectangle bounds, Dimension min, int deltaX,
                        int deltaY, int deltaWidth, int deltaHeight) {
      bounds.x += deltaX;
      bounds.y += deltaY;
      bounds.width += deltaWidth;
      bounds.height += deltaHeight;
      if (min != null) {
        if (bounds.width < min.width) {
          int correction = min.width - bounds.width;
          if (deltaX != 0) {
            bounds.x -= correction;
          }
          bounds.width = min.width;
        }
        if (bounds.height < min.height) {
          int correction = min.height - bounds.height;
          if (deltaY != 0) {
            bounds.y -= correction;
          }
          bounds.height = min.height;
        }
      }
    }

    @SuppressWarnings("unchecked")
    public void mouseDragged(MouseEvent ev) {
      Window w = (Window)ev.getSource();
      Point pt = ev.getPoint();

      if (isMovingWindow) {
        Point windowPt;
        try {
          windowPt = (Point)AccessController
            .doPrivileged(getLocationAction);
          windowPt.x = windowPt.x - dragOffsetX;
          windowPt.y = windowPt.y - dragOffsetY;
          w.setLocation(windowPt);
        }
        catch (PrivilegedActionException e) {
        }
      }
      else if (dragCursor != 0) {
        Rectangle r = w.getBounds();
        Rectangle startBounds = new Rectangle(r);
        Dimension min = w.getMinimumSize();

        switch (dragCursor) {
          case Cursor.E_RESIZE_CURSOR:
            adjust(r, min, 0, 0, pt.x
                                      + (dragWidth - dragOffsetX) - r.width, 0);
            break;
          case Cursor.S_RESIZE_CURSOR:
            adjust(r, min, 0, 0, 0, pt.y
                                         + (dragHeight - dragOffsetY) - r.height);
            break;
          case Cursor.N_RESIZE_CURSOR:
            adjust(r, min, 0, pt.y - dragOffsetY, 0,
                        -(pt.y - dragOffsetY));
            break;
          case Cursor.W_RESIZE_CURSOR:
            adjust(r, min, pt.x - dragOffsetX, 0,
                        -(pt.x - dragOffsetX), 0);
            break;
          case Cursor.NE_RESIZE_CURSOR:
            adjust(r, min, 0, pt.y - dragOffsetY, pt.x
                                                            + (dragWidth - dragOffsetX) - r.width,
                        -(pt.y - dragOffsetY));
            break;
          case Cursor.SE_RESIZE_CURSOR:
            adjust(r, min, 0, 0, pt.x
                                      + (dragWidth - dragOffsetX) - r.width,
                        pt.y + (dragHeight - dragOffsetY)
                        - r.height);
            break;
          case Cursor.NW_RESIZE_CURSOR:
            adjust(r, min, pt.x - dragOffsetX, pt.y
                                                         - dragOffsetY, -(pt.x - dragOffsetX),
                        -(pt.y - dragOffsetY));
            break;
          case Cursor.SW_RESIZE_CURSOR:
            adjust(r, min, pt.x - dragOffsetX, 0,
                        -(pt.x - dragOffsetX), pt.y
                                                    + (dragHeight - dragOffsetY)
                                                    - r.height);
            break;
          default:
            break;
        }
        if (!r.equals(startBounds)) {
          w.setBounds(r);
          if (Toolkit.getDefaultToolkit().isDynamicLayoutActive()) {
            w.validate();
            getRootPane().repaint();
          }
        }
      }
    }

    private CursorState cursorState = CursorState.NIL;

    public void mouseEntered(MouseEvent ev) {
      Window w = (Window)ev.getSource();
      if (cursorState == CursorState.EXITED || cursorState == CursorState.NIL) {
        myLastCursor = w.getCursor();
      }
      cursorState = CursorState.ENTERED;
      mouseMoved(ev);
    }

    public void mouseExited(MouseEvent ev) {
      Window w = (Window)ev.getSource();
      w.setCursor(myLastCursor);
      cursorState = CursorState.EXITED;
    }

    public void mouseClicked(MouseEvent ev) {
      Window w = (Window)ev.getSource();
      Frame f;

      if (w instanceof Frame) {
        f = (Frame)w;
      }
      else {
        return;
      }

      JComponent windowTitlePane = getTitlePane();
      if (windowTitlePane == null) {
        return;
      }

      Point convertedPoint = SwingUtilities.convertPoint(w, ev.getPoint(), windowTitlePane);

      int state = f.getExtendedState();
      if (windowTitlePane.contains(convertedPoint)) {
        if (((ev.getClickCount() % 2) == 0)
            && ((ev.getModifiers() & InputEvent.BUTTON1_MASK) != 0)) {
          if (f.isResizable()) {
            if ((state & Frame.MAXIMIZED_BOTH) != 0) {
              setMaximized();
              f.setExtendedState(state & ~Frame.MAXIMIZED_BOTH);
            }
            else {
              setMaximized();
              f.setExtendedState(state | Frame.MAXIMIZED_BOTH);
            }
          }
        }
      }
    }

    private int calculateCorner(Window w, int x, int y) {
      Insets insets = w.getInsets();
      int xPosition = calculatePosition(x - insets.left, w
                                                                .getWidth()
                                                              - insets.left - insets.right);
      int yPosition = calculatePosition(y - insets.top, w
                                                               .getHeight()
                                                             - insets.top - insets.bottom);

      if ((xPosition == -1) || (yPosition == -1)) {
        return -1;
      }
      return yPosition * 5 + xPosition;
    }

    private int getCursor(int corner) {
      if (corner == -1) {
        return 0;
      }
      return cursorMapping[corner];
    }

    private int calculatePosition(int spot, int width) {
      if (spot < BORDER_DRAG_THICKNESS) {
        return 0;
      }
      if (spot < CORNER_DRAG_WIDTH) {
        return 1;
      }
      if (spot >= (width - BORDER_DRAG_THICKNESS)) {
        return 4;
      }
      if (spot >= (width - CORNER_DRAG_WIDTH)) {
        return 3;
      }
      return 2;
    }
  }

  private class TitleMouseInputHandler extends MouseInputAdapter {
    private Point dragOffset = new Point(0, 0);

    @Override
    public void mousePressed(MouseEvent ev) {
      JRootPane rootPane = getRootPane();

      if (rootPane.getWindowDecorationStyle() == JRootPane.NONE) {
        return;
      }

      Point dragWindowOffset = ev.getPoint();
      Component source = (Component)ev.getSource();

      Point convertedDragWindowOffset = SwingUtilities.convertPoint(
        source, dragWindowOffset, getTitlePane());

      dragWindowOffset = SwingUtilities.convertPoint(source, dragWindowOffset, myWindow);

      if (getTitlePane() != null && getTitlePane().contains(convertedDragWindowOffset)) {
        if (myWindow != null) {
          myWindow.toFront();
          dragOffset = dragWindowOffset;
        }
      }
    }

    @Override
    public void mouseDragged(MouseEvent ev) {
      Component source = (Component)ev.getSource();

      Point eventLocationOnScreen = ev.getLocationOnScreen();
      if (eventLocationOnScreen == null) {
        eventLocationOnScreen = new Point(ev.getX() + source.getLocationOnScreen().x, ev.getY() + source.getLocationOnScreen().y);
      }
      if (myWindow instanceof Frame) {
        Frame f = (Frame)myWindow;
        int frameState = f.getExtendedState();
        
        if (((frameState & Frame.MAXIMIZED_BOTH) == 0)) {
          myWindow.setLocation(eventLocationOnScreen.x - dragOffset.x, eventLocationOnScreen.y - dragOffset.y);
        }
      }
      else {
        myWindow.setLocation(eventLocationOnScreen.x - dragOffset.x, eventLocationOnScreen.y - dragOffset.y);
      }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      Frame f;

      if (myWindow instanceof Frame) {
        f = (Frame)myWindow;
      }
      else {
        return;
      }

      Point convertedPoint = SwingUtilities.convertPoint(myWindow, e.getPoint(), getTitlePane());

      int state = f.getExtendedState();
      if ((getTitlePane() != null) && getTitlePane().contains(convertedPoint)) {
        if (((e.getClickCount() % 2) == 0) && ((e.getModifiers() & InputEvent.BUTTON1_MASK) != 0)) {
          if (f.isResizable()) {
            if ((state & Frame.MAXIMIZED_BOTH) != 0) {
              setMaximized();
              f.setExtendedState(state & ~Frame.MAXIMIZED_BOTH);
            }
            else {
              setMaximized();
              f.setExtendedState(state | Frame.MAXIMIZED_BOTH);
            }
          }
        }
      }
    }
  }
}