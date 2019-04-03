// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.DataManager;
import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import com.intellij.openapi.wm.impl.IdeRootPane;
import com.intellij.openapi.wm.impl.customFrameDecorations.FrameBorderlessActions;
import com.intellij.openapi.wm.impl.customFrameDecorations.titleLabel.CustomDecorationPath;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import static java.awt.Frame.MAXIMIZED_BOTH;
import static java.awt.Frame.MAXIMIZED_VERT;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTitlePane extends JPanel implements Disposable {

  private PropertyChangeListener myPropertyChangeListener;
  private ComponentListener myComponentListener;
  private JComponent productIcon;
  private JMenuBar myIdeMenu;
  private WindowListener myWindowListener;
  private Window myWindow;
  private final JRootPane myRootPane;
  private int myState;

  private JComponent buttonPanes;
  private final JLabel titleLabel = new JLabel();
  private final CustomDecorationPath mySelectedEditorFilePath = new CustomDecorationPath(this);

  private final Color myInactiveForeground = UIManager.getColor("inactiveCaptionText");
  private Color myActiveForeground = null;
  private boolean myIsActive;
  private MyTopBorder myTopBorder = new MyTopBorder();

  private static final int menuBarGap = 7;
  private static final int minMenuHeight = 24;
  private static final int resizeGap = JBUI.scale(3);

  public DarculaTitlePane(JRootPane root) {
    this.myRootPane = root;

    myState = -1;

    installSubcomponents();
    determineColors();

    setOpaque(true);
    setBackground(JBUI.CurrentTheme.CustomFrameDecorations.titlePaneBackground());
    setBorder(myTopBorder);
  }

  private void uninstall() {
    uninstallListeners();
    myWindow = null;
    removeAll();
  }

  private void installListeners() {
    if (myWindow != null) {
      myWindowListener = createWindowListener();

      myWindow.addWindowListener(myWindowListener);
      myWindow.addWindowStateListener((WindowStateListener)myWindowListener);

      myComponentListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          setCustomDecorationHitTestSpots();
        }
      };
      addComponentListener(myComponentListener);
    }
  }

  private void uninstallListeners() {
    if (myWindow != null) {
      myWindow.removeWindowListener(myWindowListener);
      removeComponentListener(myComponentListener);
    }
  }

  private WindowListener createWindowListener() {
    return new WindowHandler();
  }

  @Override
  public JRootPane getRootPane() {
    return myRootPane;
  }

  private int getWindowDecorationStyle() {
    return getRootPane().getWindowDecorationStyle();
  }

  @Override
  public void addNotify() {
    super.addNotify();

    uninstallListeners();

    myWindow = SwingUtilities.getWindowAncestor(this);
    if (myWindow != null) {
      installListeners();
    }
    setCustomDecorationHitTestSpots();
  }

  @Override
  public void removeNotify() {
    super.removeNotify();

    uninstallListeners();
    myWindow = null;
  }

  private void setCustomDecorationHitTestSpots() {
    Window window = getWindow();
    if (window == null) return;

    List<Rectangle> hitTestSpots = new ArrayList<>();

    Rectangle iconRect = productIcon == null ? null : new RelativeRectangle(productIcon).getRectangleOn(this);
    Rectangle menuRect = myIdeMenu == null ? null : new RelativeRectangle(myIdeMenu).getRectangleOn(this);
    Rectangle buttonsRect = buttonPanes == null ? null : new RelativeRectangle(buttonPanes).getRectangleOn(this);

    Frame frame = getFrame();
    if (frame != null) {
      int state = frame.getExtendedState();
      if (state != MAXIMIZED_VERT && state != MAXIMIZED_BOTH) {

        if (menuRect != null) {
          menuRect.y += Math.round(menuRect.height / 3);
        }

        if (iconRect != null) {
          iconRect.y += resizeGap;
          iconRect.x += resizeGap;
        }

        if (buttonsRect != null) {
          buttonsRect.y += resizeGap;
          buttonsRect.x += resizeGap;
          buttonsRect.width -= resizeGap;
        }
      }
    }
    if (menuRect != null)
    hitTestSpots.add(menuRect);

    hitTestSpots.addAll(mySelectedEditorFilePath.getListenerBounds());
    if (iconRect != null) {
      hitTestSpots.add(iconRect);
    }
    if (buttonsRect != null)
    hitTestSpots.add(buttonsRect);

    JdkEx.setCustomDecorationHitTestSpots(myWindow, hitTestSpots);
  }

  @Override
  public void dispose() {

  }

  private void installSubcomponents() {
    int decorationStyle = getWindowDecorationStyle();

    if (decorationStyle == JRootPane.FRAME) {
      setLayout(new MigLayout("novisualpadding, fillx, ins 0, gap 0, top", menuBarGap + "[pref!]" + menuBarGap + "[][pref!]"));

      FrameBorderlessActions actions = FrameBorderlessActions.Companion.create(myRootPane);

      buttonPanes = actions.getButtonPaneView();
      Disposer.register(actions, this);

      productIcon = actions.getProductIcon();
      add(productIcon);
      if (myRootPane instanceof IdeRootPane) {
        myIdeMenu = new IdeMenuBar(ActionManagerEx.getInstanceEx(), DataManager.getInstance()) {
          @Override
          public Border getBorder() {
            return JBUI.Borders.empty();
          }
        };

        JPanel pane = new JPanel(new MigLayout("fillx, ins 0, novisualpadding", "[pref!][]"));
        pane.setOpaque(false);
        pane.add(myIdeMenu, "wmin 0, wmax pref, top, hmin " + minMenuHeight);
        pane.add(mySelectedEditorFilePath.getView(), "center, growx, wmin 0, gapbefore " + menuBarGap + ", gapafter " + menuBarGap);

        add(pane, "wmin 0, growx");
      }
      else {
        add(titleLabel, "growx, snap 2");
      }
      add(actions.getButtonPaneView(), "top, wmin pref");
    } else /*if (decorationStyle == JRootPane.PLAIN_DIALOG ||
             decorationStyle == JRootPane.INFORMATION_DIALOG ||
             decorationStyle == JRootPane.ERROR_DIALOG ||
             decorationStyle == JRootPane.COLOR_CHOOSER_DIALOG ||
             decorationStyle == JRootPane.FILE_CHOOSER_DIALOG ||
             decorationStyle == JRootPane.QUESTION_DIALOG ||
             decorationStyle == JRootPane.WARNING_DIALOG)*/ {
      setLayout(new MigLayout("fillx, novisualpadding, ins 0, gap 0", menuBarGap + "[min!]" + menuBarGap + "[][pref!]"));


      FrameBorderlessActions actions = FrameBorderlessActions.Companion.create(myRootPane);
      productIcon = actions.getProductIcon();
      add(productIcon);
      add(titleLabel, "growx");
      titleLabel.setText(getTitle());

      buttonPanes = actions.getButtonPaneView();
      Disposer.register(actions, this);
      add(buttonPanes, "top, wmin pref");
    }
  }

  private void determineColors() {
    switch (getWindowDecorationStyle()) {
      case JRootPane.ERROR_DIALOG:
        myActiveForeground = UIManager.getColor("OptionPane.errorDialog.titlePane.foreground");
        break;
      case JRootPane.QUESTION_DIALOG:
      case JRootPane.COLOR_CHOOSER_DIALOG:
      case JRootPane.FILE_CHOOSER_DIALOG:
        myActiveForeground = UIManager.getColor("OptionPane.questionDialog.titlePane.foreground");
        break;
      case JRootPane.WARNING_DIALOG:
        myActiveForeground = UIManager.getColor("OptionPane.warningDialog.titlePane.foreground");
        break;
      case JRootPane.PLAIN_DIALOG:
      case JRootPane.INFORMATION_DIALOG:
      default:
        myActiveForeground = UIManager.getColor("activeCaptionText");
        break;
    }
  }

  private void setActive(boolean isSelected) {
    titleLabel.setForeground(isSelected ? myActiveForeground : myInactiveForeground);
    myIsActive = isSelected;

    repaintTopBorderArea();
  }

  private void repaintTopBorderArea() {
    repaint(0, 0, getWidth(), MyTopBorderConsts.THICKNESS);
  }

  private Frame getFrame() {
    Window window = getWindow();

    if (window instanceof Frame) {
      return (Frame)window;
    }
    return null;
  }

  private Window getWindow() {
    return myWindow;
  }

  private String getTitle() {
    Window w = getWindow();

    if (w instanceof Frame) {
      return ((Frame)w).getTitle();
    }
    else if (w instanceof Dialog) {
      return ((Dialog)w).getTitle();
    }
    return null;
  }

  private class WindowHandler extends WindowAdapter {
    @Override
    public void windowActivated(WindowEvent ev) {
      setActive(true);
    }

    @Override
    public void windowDeactivated(WindowEvent ev) {
      setActive(false);
    }

    @Override
    public void windowStateChanged(WindowEvent e) {
      //noinspection ConstantConditions
      Frame frame = getFrame();
      if (frame == null) return;

      myTopBorder.setState(frame.getExtendedState());
    }
  }

  private interface MyTopBorderConsts {
    int THICKNESS = 1;
    Color MENUBAR_BORDER_COLOR = JBColor.namedColor("MenuBar.borderColor", new JBColor(Gray.xCD, Gray.x51));
    Color ACTIVE_COLOR = ObjectUtils.notNull((Color)Toolkit.getDefaultToolkit().getDesktopProperty("win.dwm.colorizationColor"),
                                             () -> (Color)Toolkit.getDefaultToolkit().getDesktopProperty("win.frame.activeBorderColor"));
    Color INACTIVE_COLOR = (Color)Toolkit.getDefaultToolkit().getDesktopProperty("win.3d.shadowColor");
  }

  private class MyTopBorder implements Border {
    private int state = Frame.NORMAL;

    public void setState(int value) {
      state = value;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {

      if (topNeeded()) {
        g.setColor(myIsActive ? MyTopBorderConsts.ACTIVE_COLOR : MyTopBorderConsts.INACTIVE_COLOR);
        LinePainter2D.paint((Graphics2D)g, x, y, width, y);
      }

      g.setColor(MyTopBorderConsts.MENUBAR_BORDER_COLOR);
      int y1 = y + height - JBUI.scale(MyTopBorderConsts.THICKNESS);
      LinePainter2D.paint((Graphics2D)g, x, y1, width, y1);
    }

    private boolean topNeeded() {
      return (state != MAXIMIZED_VERT) && (state != MAXIMIZED_BOTH);
    }

    @Override
    public Insets getBorderInsets(Component c) {
      int scale = JBUI.scale(MyTopBorderConsts.THICKNESS);
      return topNeeded() ? new Insets(MyTopBorderConsts.THICKNESS, 0, scale, 0) : new Insets(0, 0, scale, 0);
    }

    @Override
    public boolean isBorderOpaque() {
      return true;
    }
  }
}
