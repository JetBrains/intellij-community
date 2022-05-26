// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.TimerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.DefaultMenuLayout;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * @author ignatov
 */
public class JBPopupMenu extends JPopupMenu {

  private static final Logger LOG = Logger.getInstance(JBPopupMenu.class);

  private final MyLayout myLayout;

  public JBPopupMenu() {
    this(null);
  }

  public JBPopupMenu(String label) {
    super(label);
    enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK);
    myLayout = new MyLayout(this);
    setLayout(myLayout);
    setLightWeightPopupEnabled(false);
    setOpaque(false);
  }

  @Override
  public void processMouseWheelEvent(MouseWheelEvent e) {
    if (!isShowing()) return;

    int rotation = e.getWheelRotation();
    if (rotation == 0) return;
    if (e.getComponent() != this) {
      e = (MouseWheelEvent)SwingUtilities.convertMouseEvent(e.getComponent(), e, this);
    }
    Point p = e.getPoint();
    SwingUtilities.convertPointToScreen(p, this);
    Point tPoint = getLocationOnScreen();
    if (p.x >= tPoint.x && p.x <= tPoint.x + getWidth() && p.y >= tPoint.y && p.y <= tPoint.y + getHeight()) {
      myLayout.updateShift(rotation * 10);
    }
  }

  @Override
  public void setLayout(LayoutManager mgr) {
    if (!(mgr instanceof MyLayout)) return;
    super.setLayout(mgr);
  }


  @Override
  public void paint(Graphics g) {
    GraphicsUtil.setupAntialiasing(g);
    super.paint(g);
    LayoutManager layout = getLayout();
    if (layout instanceof MyLayout) {
      ((MyLayout)layout).paintIfNeeded(g);
    }
  }

  /**
   * @param component the component above which the popup menu is to appear
   * @param menu      the popup menu to show above the specified component
   */
  public static void showAbove(@NotNull Component component, @NotNull JPopupMenu menu) {
    // We need `menu.show(component, 0, -menu.getHeight());`, but `menu.getHeight()` will return 0 if the menu hasn't been shown yet.
    // Let's show it somewhere, and once it's shown, move it to the desired location.
    menu.show(component, 0, 0);
    UiNotifyConnector.doWhenFirstShown(menu, () -> {
      Window window = UIUtil.getWindow(menu);
      if (window == null) {
        LOG.error("Cannot find window for menu popup " + menu + ", " + menu.isShowing());
      }
      else {
        Point diff = SwingUtilities.convertPoint(component, 0, 0, window);
        window.setLocation(window.getX(), window.getY() + diff.y - window.getHeight());
      }
    });
  }

  /**
   * @param component the component near which the popup menu is to appear
   * @param menu      the popup menu to show near the specified component
   */
  public static void showAtRight(@NotNull Component component, @NotNull JPopupMenu menu) {
    menu.show(component, component.getWidth(), 0);
  }

  /**
   * @param component the component below which the popup menu is to appear
   * @param menu      the popup menu to show below the specified component
   */
  public static void showBelow(@NotNull Component component, @NotNull JPopupMenu menu) {
    menu.show(component, 0, component.getHeight());
  }

  /**
   * @param component the component below which the popup menu is to appear
   * @param place     the place used for {@link AnActionEvent}
   * @param group     Group from which the actions for the menu are taken.
   * @see ActionManager#createActionPopupMenu(String, ActionGroup)
   */
  public static void showBelow(@NotNull Component component, @NonNls @NotNull String place, @NotNull ActionGroup group) {
    showBelow(component, ActionManager.getInstance().createActionPopupMenu(place, group).getComponent());
  }

  /**
   * @param event the mouse event that specifies a popup position
   * @param menu  the popup menu to show in the given mouse position
   */
  public static void showByEvent(@NotNull MouseEvent event, @NotNull JPopupMenu menu) {
    menu.show(event.getComponent(), event.getX(), event.getY());
  }

  public static void showByEditor(@NotNull Editor editor, @NotNull JPopupMenu menu) {
    Component invoker = editor.getContentComponent();
    Point caretPoint = editor.visualPositionToXY(editor.getCaretModel().getVisualPosition());
    menu.show(invoker, caretPoint.x, caretPoint.y);
  }

  /**
   * @param event the mouse event that specifies a popup position
   * @param place the place used for {@link AnActionEvent}
   * @param group Group from which the actions for the menu are taken.
   * @see ActionManager#createActionPopupMenu(String, ActionGroup)
   */
  public static void showByEvent(@NotNull MouseEvent event, @NonNls @NotNull String place, @NotNull ActionGroup group) {
    showByEvent(event, ActionManager.getInstance().createActionPopupMenu(place, group).getComponent());
  }

  /**
   * @param point the relative point that specifies a popup position
   * @param menu  the popup menu to show in the given mouse position
   */
  public static void showAt(@NotNull RelativePoint point, @NotNull JPopupMenu menu) {
    menu.show(point.getComponent(), point.getPoint().x, point.getPoint().y);
  }

  private static class MyLayout extends DefaultMenuLayout implements ActionListener {
    private final JPopupMenu myTarget;
    int myShift = 0;
    int myScrollDirection = 0;
    Timer myTimer;

    MyLayout(final JPopupMenu target) {
      super(target, BoxLayout.PAGE_AXIS);
      myTarget = target;
      myTimer = TimerUtil.createNamedTimer("PopupTimer", 40, this);
      myTarget.addPopupMenuListener(new PopupMenuListener() {
        @Override
        public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
          switchTimer(true);
        }

        @Override
        public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
          switchTimer(false);
          JRootPane rootPane = SwingUtilities.getRootPane(target);
          if (rootPane != null) {
            rootPane.putClientProperty("apple.awt._windowFadeDelegate", null);
          }
        }

        @Override
        public void popupMenuCanceled(PopupMenuEvent e) {
          switchTimer(false);
        }
      });
      if (myTarget.isVisible()) {
        switchTimer(true);
      }
    }

    private void switchTimer(boolean on) {
      if (on && !myTimer.isRunning()) {
        myTimer.start();
      }
      if (!on && myTimer.isRunning()) {
        myTimer.stop();
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (!myTarget.isShowing()) return;
      PointerInfo info = MouseInfo.getPointerInfo();
      if (info == null) return;
      Point mouseLocation = info.getLocation();
      Point targetLocation = myTarget.getLocationOnScreen();
      if (mouseLocation.x < targetLocation.x || mouseLocation.x > targetLocation.x + myTarget.getWidth()) {
        return;
      }
      if (Math.abs(mouseLocation.y - targetLocation.y - getMaxHeight()) < 10) {
        myScrollDirection = 1;
      }
      else if (Math.abs(mouseLocation.y - targetLocation.y) < 10) {
        myScrollDirection = -1;
      }
      else {
        return;
      }

      SwingUtilities.convertPointFromScreen(mouseLocation, myTarget);
      myTarget.dispatchEvent(
        new MouseEvent(myTarget, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, mouseLocation.x, mouseLocation.y, 0, false));

      updateShift(5 * myScrollDirection);
    }

    private void updateShift(int increment) {
      int maxHeight = super.preferredLayoutSize(myTarget).height - getMaxHeight();
      int newShift = Math.max(0, Math.min(maxHeight, myShift + increment));
      if (newShift != myShift) {
        myShift = newShift;
        myTarget.revalidate();
        myTarget.repaint();
        Window w = ComponentUtil.getWindow(myTarget.getComponent());
        if (w != null) {
          for (Window window : w.getOwnedWindows()) {
            window.dispose();
          }
        }
      }
    }

    private final Color[] dim = new Color[]{
      JBColor.background(),
      ColorUtil.withAlpha(JBColor.background(), .9),
      ColorUtil.withAlpha(JBColor.background(), .8),
      ColorUtil.withAlpha(JBColor.background(), .7),
      ColorUtil.withAlpha(JBColor.background(), .6),
      ColorUtil.withAlpha(JBColor.background(), .5),
      ColorUtil.withAlpha(JBColor.background(), .4),
      ColorUtil.withAlpha(JBColor.background(), .3),
      ColorUtil.withAlpha(JBColor.background(), .2),
      ColorUtil.withAlpha(JBColor.background(), .1),
    };

    public void paintIfNeeded(Graphics g) {
      if (myShift > 0) {
        for (int i = 0; i < dim.length; i++) {
          g.setColor(dim[i]);
          g.drawLine(0, i, myTarget.getWidth(), i);
        }
        AllIcons.General.ArrowUp.paintIcon(myTarget, g, myTarget.getWidth() / 2 - AllIcons.General.ArrowUp.getIconWidth() / 2, 0);
      }
      if (super.preferredLayoutSize(myTarget).height - getMaxHeight() - myShift > 0) {
        for (int i = 0; i < dim.length; i++) {
          g.setColor(dim[i]);
          g.drawLine(0, myTarget.getHeight() - i, myTarget.getWidth(),
                     myTarget.getHeight() - i);
        }
        AllIcons.General.ArrowDown.paintIcon(myTarget, g, myTarget.getWidth() / 2 - AllIcons.General.ArrowDown.getIconWidth() / 2,
                                             myTarget.getHeight() - AllIcons.General.ArrowDown.getIconHeight());
      }
    }

    @Override
    public void layoutContainer(Container target) {
      Insets insets = target.getInsets();
      int width = target.getWidth() - insets.left - insets.right;
      Component[] components = target.getComponents();
      int y = -myShift + insets.top;
      for (Component component : components) {
        if (!component.isVisible()) {
          String place = target instanceof PlaceProvider ? ((PlaceProvider)target).getPlace() : null;
          String itemText = component instanceof AbstractButton ?
                            "\"" + ((AbstractButton)component).getText() + "\"" : component.getClass().getName();
          LOG.error("Invisible menu item " + itemText + (place != null ? " in '" + place + "'" : "") +
                    " ("  + component.getClass().getName() + " in " + target.getClass().getName() + ")");
          continue;
        }
        int height = component.getPreferredSize().height;
        component.setBounds(insets.left, y, width, height);
        y += height;
      }
    }

    private int getMaxHeight() {
      GraphicsConfiguration configuration = myTarget.getGraphicsConfiguration();
      if (configuration == null && myTarget.getInvoker() != null) {
        configuration = myTarget.getInvoker().getGraphicsConfiguration();
      }
      if (configuration == null) return Short.MAX_VALUE;
      Rectangle screenRectangle = ScreenUtil.getScreenRectangle(configuration);
      return screenRectangle.height;
    }

    @NotNull
    @Override
    public Dimension preferredLayoutSize(Container target) {
      Dimension dimension = super.preferredLayoutSize(target);
      int maxHeight = getMaxHeight();
      switchTimer(dimension.height > maxHeight);
      dimension.height = Math.min(maxHeight, dimension.height);
      return dimension;
    }
  }
}
