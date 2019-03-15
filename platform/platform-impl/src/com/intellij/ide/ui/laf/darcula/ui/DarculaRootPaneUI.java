// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.openapi.wm.impl.IdeRootPane;
import com.intellij.openapi.wm.impl.WindowManagerImpl;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicRootPaneUI;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaRootPaneUI extends BasicRootPaneUI {
  private Window myWindow;

  private JComponent myTitlePane;

  private LayoutManager myLayoutManager;

  private LayoutManager myOldLayout;

  private JRootPane myRootPane;

  private PropertyChangeListener myPropertyChangeListener;

  private Window myCurrentWindow;


  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent comp) {
    return customDecorationNeeded(comp) ? new DarculaRootPaneUI() : createDefaultWindowsRootPaneUI();
  }

  private static boolean customDecorationNeeded(JComponent comp) {
    return comp instanceof IdeRootPane && IdeFrameDecorator.isCustomDecoration();
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
    myRootPane = (JRootPane)c;
    super.installUI(c);

    installLayout(myRootPane);

    updateState();
  }

  private void updateState() {
    if (myRootPane == null) return;

    JMenuBar bar = myRootPane.getJMenuBar();
    if (bar != null) {
      bar.setVisible(isInFullScreen());
    }

    if (isInFullScreen()) {
      uninstallClientDecorations(myRootPane);
      return;
    }

    int style = myRootPane.getWindowDecorationStyle();
    if (style != JRootPane.NONE) {
      installClientDecorations(myRootPane);
    }
  }

  private boolean isInFullScreen() {
    Window window = SwingUtilities.getWindowAncestor(myRootPane);
    return window != null && (window instanceof IdeFrameEx && ((IdeFrameEx)window).isInFullScreen()) && myRootPane.getJMenuBar() != null;
  }

  @Override
  public void uninstallUI(JComponent c) {
    super.uninstallUI(c);

    uninstallClientDecorations(myRootPane);
    uninstallLayout(myRootPane);

    myLayoutManager = null;
    myRootPane = null;
  }

  private void installWindowListeners(JRootPane root, Component parent) {

  }

  private void uninstallWindowListeners(JRootPane root) {

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
    myPropertyChangeListener = evt -> updateState();

    root.addPropertyChangeListener(WindowManagerImpl.FULL_SCREEN, myPropertyChangeListener);
  }


  @Override
  protected void uninstallListeners(JRootPane root) {
    root.removePropertyChangeListener(myPropertyChangeListener);
    myPropertyChangeListener = null;

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
    JComponent titlePane = createTitlePane(root);

    setTitlePane(root, titlePane);
    if (SwingUtilities.getWindowAncestor(myRootPane) != null) {
      root.revalidate();
      root.repaint();
    }
  }

  private void uninstallClientDecorations(JRootPane root) {
   // uninstallBorder(root);
    //uninstallWindowListeners(root);
    setTitlePane(root, null);

    int style = root.getWindowDecorationStyle();
    if (style == JRootPane.NONE) {
      root.repaint();
      root.revalidate();
    }
  }

  protected JComponent createTitlePane(JRootPane root) {
    return new DarculaTitlePane(root, this);
  }

  protected LayoutManager createLayoutManager() {
    return new DarculaRootLayout();
  }

  private void setTitlePane(JRootPane root, JComponent titlePane) {
    JLayeredPane layeredPane = root.getLayeredPane();
    disposeTitle(layeredPane);
    if (titlePane != null) {
      layeredPane.add(titlePane, JLayeredPane.FRAME_CONTENT_LAYER);
      titlePane.setVisible(true);
    }
    myTitlePane = titlePane;
  }

  private void disposeTitle(JLayeredPane layeredPane) {
    JComponent oldTitlePane = getTitlePane();

    if (oldTitlePane != null) {
      layeredPane.remove(oldTitlePane);
      if(oldTitlePane instanceof Disposable) {
        Disposer.dispose((Disposable)oldTitlePane);
      }
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
      updateState();
    }
/*    if (propertyName.equals("ancestor")) {
      uninstallWindowListeners(myRootPane);
      if (e.getNewValue() != null && ((JRootPane)e.getSource()).getWindowDecorationStyle() != JRootPane.NONE) {
        installWindowListeners(myRootPane, myRootPane.getParent());
      }
    }*/
  }

  protected static class DarculaRootLayout implements LayoutManager2 {
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

      JMenuBar bar = root.getJMenuBar();
      if (bar != null && bar.isVisible()) {
        Dimension mbd = bar.getPreferredSize();
        bar.setBounds(0, nextY, w, mbd.height);
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