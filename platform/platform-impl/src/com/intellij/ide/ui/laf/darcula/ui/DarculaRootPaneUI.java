// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.utils.RdIdeaKt;
import com.intellij.ui.FrameState;
import com.intellij.ui.Gray;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.WindowResizeListener;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.rider.util.lifetime.Lifetime;
import com.jetbrains.rider.util.lifetime.LifetimeDefinition;
import kotlin.Unit;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicRootPaneUI;
import java.awt.*;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaRootPaneUI extends BasicRootPaneUI {
  protected JRootPane myRootPane;
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent comp) {
    return JBUI.isCustomFrameDecoration() ? new DarculaRootPaneUI() : createDefaultWindowsRootPaneUI();
  }

  private static ComponentUI createDefaultWindowsRootPaneUI() {
    try {
      return (ComponentUI)Class.forName("com.sun.java.swing.plaf.windows.WindowsRootPaneUI").newInstance();
    } catch (Exception e) {
      return new BasicRootPaneUI();
    }
  }

  private Window myWindow;

  private JComponent myTitlePane;

  private MouseInputListener myMouseInputListener;

  private MouseInputListener myTitleMouseInputListener;

  private LayoutManager myLayoutManager;

  private LayoutManager myOldLayout;

  protected WindowListener myWindowListener;

  protected Window myCurrentWindow;

  protected ComponentListener myWindowComponentListener;

  protected GraphicsConfiguration currentRootPaneGC;

  @Override
  public void installUI(JComponent c) {
    myRootPane = (JRootPane)c;
    super.installUI(c);

    if (JBUI.isCustomFrameDecoration()) {
      int style = myRootPane.getWindowDecorationStyle();
      if (style != JRootPane.NONE) {
        installClientDecorations(myRootPane);
      }
    }
  }

  public void installMenuBar(JMenuBar menu) {
    if (menu != null && JBUI.isCustomFrameDecoration()) {
      getTitlePane().add(menu);
    }
  }

  @Override
  public void uninstallUI(JComponent c) {
    super.uninstallUI(c);

    if (JBUI.isCustomFrameDecoration()) {
      uninstallClientDecorations(myRootPane);
      myLayoutManager = null;
      myMouseInputListener = null;
      myRootPane = null;
    }
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
        //noinspection UseDPIAwareInsets
        myMouseInputListener = new WindowResizeListener(parent, JBUI.insets(11), null) {
          @Override
          protected Insets getResizeOffset(Component view) {
            return getResizeBorder(view);
          }
        };
      }
      myWindow.addMouseListener(myMouseInputListener);
      myWindow.addMouseMotionListener(myMouseInputListener);

      if (myTitlePane != null) {
        if (myTitleMouseInputListener == null) {
          myTitleMouseInputListener = new WindowMoveListener(myTitlePane) {
            @Override
            public void mousePressed(MouseEvent event) {
              if (JBUI.isCustomFrameDecoration()) {
                Component view = getView(getContent(event));
                if (view instanceof Frame) {
                  Frame frame = (Frame)view;
                  int state = frame.getExtendedState();

                  // System.out.println(view.getBounds());

                  if (FrameState.isMaximized(state)) {
                    frame.setExtendedState((state & ~Frame.MAXIMIZED_BOTH));
                    updateFrameBounds(frame, event);
                  }
                }
              }
              super.mousePressed(event);
            }

            private void updateFrameBounds(Frame frame, MouseEvent event) {
              Point mouse = event.getLocationOnScreen();

              Rectangle screenBounds = frame.getMaximizedBounds();
              Rectangle frameBounds = frame.getBounds();

              int x = mouse.x - (frameBounds.width / 2);

              if (x < screenBounds.x) {
                x = screenBounds.x;
              }
              else {
                int rightPoint = screenBounds.x + screenBounds.width;
                if (x + frameBounds.width > rightPoint) {
                  x = rightPoint - frameBounds.width;
                }
              }
              int y = mouse.y - (myTitlePane.getHeight() / 2);

              frameBounds.x = x;
              frameBounds.y = y;

              frame.setBounds(frameBounds);
            }

            @Override
            protected boolean isDisabled(Component view) {
              if (view instanceof RootPaneContainer) {
                RootPaneContainer container = (RootPaneContainer)view;
                JRootPane pane = container.getRootPane();
                if (pane != null && JRootPane.NONE == pane.getWindowDecorationStyle()) return true;
              }
              return super.isDisabled(view);
            }

            @Override
            protected boolean isDisabledInMaximizedBoth() {
              return !JBUI.isCustomFrameDecoration();
            }
          };
        }
        myTitlePane.addMouseMotionListener(myTitleMouseInputListener);
        myTitlePane.addMouseListener(myTitleMouseInputListener);
      }
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

  private LifetimeDefinition myLifetimeDefinition = null;

  @Override
  protected void installListeners(final JRootPane root) {
    super.installListeners(root);
    myLifetimeDefinition = Lifetime.Companion.getEternal().createNestedDef();

    Lifetime lt = myLifetimeDefinition.getLifetime();
    RdIdeaKt.window(root).advise(lt, window -> {
                                   myWindow = window;
                                   if(window != null) {
                                     RdIdeaKt.screenInfo(myWindow).advise(lt, screenInfo -> {
                                       if (window instanceof JFrame) {
                                         ((JFrame)window).setMaximizedBounds(screenInfo == null ? null : screenInfo.getInnerBounds());
                                         //((JFrame)window).setMaximizedBounds(null);
                                       }
                                       return Unit.INSTANCE;
                                     });
                                   }
                                   return Unit.INSTANCE;
                                 }
    );
  }

  @Override
  protected void uninstallListeners(JRootPane root) {
    if(myLifetimeDefinition != null && !myLifetimeDefinition.isTerminated())
      myLifetimeDefinition.terminate();

    if (myWindow != null) {
      myWindow.removeWindowListener(myWindowListener);
      myWindowListener = null;
      myWindow.removeComponentListener(myWindowComponentListener);
      myWindowComponentListener = null;
    }

    super.uninstallListeners(root);
  }

  /**
   * Uninstalls the previously installed {@code LayoutManager}.
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
   * decorations. This is ONLY invoked if the {@code JRootPane} has a
   * decoration style other than {@code JRootPane.NONE}.
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
      myWindow.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }
    myWindow = null;
  }

  protected JComponent createTitlePane(JRootPane root) {
    return new CustomFrameTitlePane(root, this);
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

/*  private Rectangle getScreenBounds(Component component, boolean normalize) {
    GraphicsConfiguration gc = (currentRootPaneGC != null) ? currentRootPaneGC : component.getGraphicsConfiguration();
    Rectangle screenBounds = gc.getBounds();
    if (normalize) {
      screenBounds.x = 0;
      screenBounds.y = 0;
    }
    Insets screenInsets = ScreenUtil.getScreenInsets(gc);
    return new Rectangle(screenBounds.x + screenInsets.left,
                         screenBounds.y + screenInsets.top,
                         screenBounds.width - (screenInsets.left + screenInsets.right),
                         screenBounds.height - (screenInsets.top + screenInsets.bottom));
  }*/

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
    @Override
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

      return new Dimension(max(cpWidth, mbWidth, tpWidth) + i.left + i.right, cpHeight + mbHeight + tpHeight + i.top + i.bottom);
    }

    @Override
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
      if ((root.getWindowDecorationStyle() != JRootPane.NONE) && (root.getUI() instanceof DarculaRootPaneUI)) {
        JComponent titlePane = ((DarculaRootPaneUI)root.getUI()).getTitlePane();
        if (titlePane != null) {
          tpd = titlePane.getMinimumSize();
          if (tpd != null) {
            tpWidth = tpd.width;
            tpHeight = tpd.height;
          }
        }
      }

      return new Dimension(max(cpWidth, mbWidth, tpWidth) + i.left + i.right, cpHeight + mbHeight + tpHeight + i.top + i.bottom);
    }

    @Override
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

      if ((root.getWindowDecorationStyle() != JRootPane.NONE) && (root.getUI() instanceof DarculaRootPaneUI)) {
        JComponent titlePane = ((DarculaRootPaneUI)root.getUI()).getTitlePane();
        if (titlePane != null) {
          tpd = titlePane.getMaximumSize();
          if (tpd != null) {
            tpWidth = tpd.width;
            tpHeight = tpd.height;
          }
        }
      }

      int maxHeight = max(cpHeight, mbHeight, tpHeight);
      if (maxHeight != Integer.MAX_VALUE) {
        maxHeight = cpHeight + mbHeight + tpHeight + i.top + i.bottom;
      }

      int maxWidth = max(cpWidth, mbWidth, tpWidth);

      if (maxWidth != Integer.MAX_VALUE) {
        maxWidth += i.left + i.right;
      }

      return new Dimension(maxWidth, maxHeight);
    }

    @Override
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

      if ((root.getWindowDecorationStyle() != JRootPane.NONE) && (root.getUI() instanceof DarculaRootPaneUI)) {
        JComponent titlePane = ((DarculaRootPaneUI)root.getUI()).getTitlePane();
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

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
      return 0.0f;
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
      return 0.0f;
    }

    @Override
    public void invalidateLayout(Container target) {
    }
  }

  private static int max(int a, int b, int... others) {
    int result = Math.max(a, b);
    for (int other : others) {
      result = Math.max(result, other);
    }
    return result;
  }
}