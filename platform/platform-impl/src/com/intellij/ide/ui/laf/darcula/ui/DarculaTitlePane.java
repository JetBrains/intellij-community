// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.CustomFrameIdeMenuBar;
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.CustomFrameTitleButtons;
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.DescriptionLabel;
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.ResizableCustomFrameTitleButtons;
import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.IdeRootPane;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.ObjectUtils;
import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUIScale.ScaleContext;
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

import static com.intellij.util.ui.JBUIScale.ScaleType.USR_SCALE;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTitlePane extends JPanel implements Disposable {

  private PropertyChangeListener myPropertyChangeListener;
  private ComponentListener myComponentListener;
  private JMenuBar myMenuBar;
  private JMenuBar myIdeMenu;
  private Action myCloseAction;
  private Action myIconifyAction;
  private Action myRestoreAction;
  private Action myMaximizeAction;
  private WindowListener myWindowListener;
  private Window myWindow;
  private final JRootPane myRootPane;
  private int myState;
  private final DarculaRootPaneUI rootPaneUI;

  private CustomFrameTitleButtons buttonPanes;
  private final JLabel titleLabel = new JLabel();
  private final DescriptionLabel projectLabel = new DescriptionLabel();

  private final Color myInactiveForeground = UIManager.getColor("inactiveCaptionText");
  private Color myActiveForeground = null;
  private boolean myIsActive;
  private Border myTopBorder = new MyTopBorder();

  private static final int menuBarGap = 7;
  private static final int resizeGap = JBUI.scale(3);

  public DarculaTitlePane(JRootPane root, DarculaRootPaneUI ui) {
    this.myRootPane = root;
    rootPaneUI = ui;

    myState = -1;

    installSubcomponents();
    determineColors();
    installDefaults();

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
      if (myWindow instanceof Frame) {
        setState(((Frame)myWindow).getExtendedState());
      }
      else {
        setState(0);
      }
      installListeners();
    }
    setCustomDecorationHitTestSpots();
  }

  private void setCustomDecorationHitTestSpots() {
    List<Rectangle> hitTestSpots = new ArrayList<>();

    Rectangle iconRect = new RelativeRectangle(myMenuBar).getRectangleOn(this);
    Rectangle menuRect = new RelativeRectangle(myIdeMenu).getRectangleOn(this);
    Rectangle buttonsRect = new RelativeRectangle(buttonPanes.getView()).getRectangleOn(this);

    Frame frame = getFrame();
    if (frame != null) {
      int state = frame.getExtendedState();
      if (state != MAXIMIZED_VERT && state != MAXIMIZED_BOTH) {

        menuRect.y += Math.round(menuRect.height / 3);

        iconRect.y += resizeGap;
        iconRect.x += resizeGap;

        buttonsRect.y += resizeGap;
        buttonsRect.x += resizeGap;
        buttonsRect.width -= resizeGap;
      }
    }

    hitTestSpots.add(menuRect);
    hitTestSpots.addAll(projectLabel.getListenerBoundses());
    hitTestSpots.add(iconRect);
    hitTestSpots.add(buttonsRect);

    JdkEx.setCustomDecorationHitTestSpots(myWindow, hitTestSpots);
  }

  @Override
  public void dispose() {
    Disposer.dispose(projectLabel);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();

    uninstallListeners();
    myWindow = null;
  }

  private void installSubcomponents() {
    int decorationStyle = getWindowDecorationStyle();
    if (decorationStyle == JRootPane.FRAME) {
      setLayout(new MigLayout("novisualpadding, fillx, ins 0, gap 0, top", menuBarGap + "[pref!]" + menuBarGap + "[][pref!]"));

      createActions();
      buttonPanes = ResizableCustomFrameTitleButtons.Companion.create(myCloseAction,
                                                                      myRestoreAction, myIconifyAction,
                                                                      myMaximizeAction);
      myMenuBar = createMenuBar();
      add(myMenuBar);
      if (myRootPane instanceof IdeRootPane) {
        myIdeMenu = new CustomFrameIdeMenuBar(ActionManagerEx.getInstanceEx(), DataManager.getInstance(), this);

        JPanel pane = new JPanel(new MigLayout("fillx, ins 0, novisualpadding", "[pref!][]"));
        pane.setOpaque(false);
        pane.add(myIdeMenu, "wmin 0, wmax pref, top, hmin 23");
        pane.add(projectLabel.getView(), "center, growx, wmin 0, gapbefore " + menuBarGap + ", gapafter " + menuBarGap);

        add(pane, "wmin 0, growx");
      }
      else {
        add(titleLabel, "growx, snap 2");
      }
      add(buttonPanes.getView(), "top, wmin pref");
    }
    else if (decorationStyle == JRootPane.PLAIN_DIALOG ||
             decorationStyle == JRootPane.INFORMATION_DIALOG ||
             decorationStyle == JRootPane.ERROR_DIALOG ||
             decorationStyle == JRootPane.COLOR_CHOOSER_DIALOG ||
             decorationStyle == JRootPane.FILE_CHOOSER_DIALOG ||
             decorationStyle == JRootPane.QUESTION_DIALOG ||
             decorationStyle == JRootPane.WARNING_DIALOG) {
      setLayout(new MigLayout("fill, novisualpadding, ins 0, gap 0", menuBarGap + "[pref!]push[pref!]"));

      createActions();
      // titleLabel.setIcon(mySystemIcon);
      add(titleLabel, "growx");
      buttonPanes = CustomFrameTitleButtons.Companion.create(myCloseAction);
      add(buttonPanes.getView());
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
    //myActiveForeground = JBColor.foreground();//UIManager.getColor("activeCaptionText");
  }

  private void installDefaults() {
    setFont(JBUI.Fonts.label().asBold());
  }


  protected JMenuBar createMenuBar() {
    myMenuBar = new JMenuBar() {
      private final ScaleContext.Cache<Icon> myIconProvider =
        new ScaleContext.Cache<>(ctx -> ObjectUtils.notNull(AppUIUtil.loadHiDPIApplicationIcon(ctx, 16), AllIcons.Icon_small));

      private Icon getIcon() {

        if (myWindow == null) return AllIcons.Icon_small;

        ScaleContext ctx = ScaleContext.create(myWindow);
        ctx.overrideScale(USR_SCALE.of(1));
        return myIconProvider.getOrProvide(ctx);
      }

      @Override
      public Dimension getPreferredSize() {
        return getMinimumSize();
      }

      @Override
      public Dimension getMinimumSize() {
        Icon icon = getIcon();
        return new Dimension(icon.getIconWidth(), icon.getIconHeight());
      }

      @Override
      public void paint(Graphics g) {
        getIcon().paintIcon(this, g, 0, 0);
      }
    };

    JMenu menu = new JMenu() {
      @Override
      public Dimension getPreferredSize() {
        return myMenuBar.getPreferredSize();
      }
    };
    myMenuBar.add(menu);

    myMenuBar.setOpaque(false);
    menu.setFocusable(false);
    menu.setBorderPainted(true);

    if (getWindowDecorationStyle() == JRootPane.FRAME) {
      addMenuItems(menu);
    }
    return myMenuBar;
  }

  private void close() {
    Window window = getWindow();

    if (window != null) {
      Disposer.dispose(this);
      window.dispatchEvent(new WindowEvent(
        window, WindowEvent.WINDOW_CLOSING));
    }
  }

  private void iconify() {
    Frame frame = getFrame();
    if (frame != null) {
      frame.setExtendedState(myState | Frame.ICONIFIED);
    }
  }

  private void maximize() {
    Frame frame = getFrame();
    if (frame != null) {
      frame.setExtendedState(myState | Frame.MAXIMIZED_BOTH);
    }
  }

  private void restore() {
    Frame frame = getFrame();

    if (frame == null) {
      return;
    }

    if ((myState & Frame.ICONIFIED) != 0) {
      frame.setExtendedState(myState & ~Frame.ICONIFIED);
    }
    else {
      frame.setExtendedState(myState & ~Frame.MAXIMIZED_BOTH);
    }
  }

  private void createActions() {
    myCloseAction = new CloseAction();

    if (getWindowDecorationStyle() == JRootPane.FRAME) {
      myIconifyAction = new IconifyAction();
      myRestoreAction = new RestoreAction();
      myMaximizeAction = new MaximizeAction();
    }
  }

  private void addMenuItems(JMenu menu) {
    menu.add(myRestoreAction);
    menu.add(myIconifyAction);
    if (Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH)) {
      menu.add(myMaximizeAction);
    }

    menu.add(new JSeparator());

    JMenuItem closeMenuItem = menu.add(myCloseAction);
    closeMenuItem.setFont(getFont().deriveFont(Font.BOLD));
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (getFrame() != null) {
      setState(getFrame().getExtendedState());
    }
    super.paintComponent(g);
  }

  private void setState(int state) {
    setState(state, false);
  }

  private void setState(int state, boolean updateRegardless) {
    Window wnd = getWindow();
    titleLabel.setText(getTitle());

    if (wnd != null && getWindowDecorationStyle() == JRootPane.FRAME) {
      if (myState == state && !updateRegardless) {
        return;
      }
      Frame frame = getFrame();

      if (frame != null) {
        if (frame.isResizable()) {
          if ((state & Frame.MAXIMIZED_BOTH) != 0) {
            myMaximizeAction.setEnabled(false);
            myRestoreAction.setEnabled(true);
          }
          else {
            myMaximizeAction.setEnabled(true);
            myRestoreAction.setEnabled(false);
          }
        }
        else {
          myMaximizeAction.setEnabled(false);
          myRestoreAction.setEnabled(false);
        }
      }
      else {
        // Not contained in a Frame
        myMaximizeAction.setEnabled(false);
        myRestoreAction.setEnabled(false);
        myIconifyAction.setEnabled(false);
      }
      myCloseAction.setEnabled(true);
      myState = state;
    }

    if (buttonPanes != null) buttonPanes.updateVisibility();
  }

  private void setActive(boolean isSelected) {
    buttonPanes.setSelected(isSelected);
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

  private class CloseAction extends AbstractAction {
    CloseAction() {
      super("Close", AllIcons.Windows.CloseSmall);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      close();
    }
  }


  private class IconifyAction extends AbstractAction {
    IconifyAction() {
      super("Minimize", AllIcons.Windows.MinimizeSmall);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      iconify();
    }
  }


  private class RestoreAction extends AbstractAction {
    RestoreAction() {
      super("Restore", AllIcons.Windows.RestoreSmall);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      restore();
    }
  }


  private class MaximizeAction extends AbstractAction {
    MaximizeAction() {
      super("Maximize", AllIcons.Windows.MaximizeSmall);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      maximize();
    }
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
      int state = getFrame().getExtendedState();
      if (state == MAXIMIZED_VERT || state == MAXIMIZED_BOTH) {
        setBorder(null);
      } else {
        setBorder(myTopBorder);
      }
    }
  }

  private interface MyTopBorderConsts {
    int THICKNESS = 1;
    Color ACTIVE_COLOR = ObjectUtils.notNull((Color)Toolkit.getDefaultToolkit().getDesktopProperty("win.dwm.colorizationColor"),
                                             () -> (Color)Toolkit.getDefaultToolkit().getDesktopProperty("win.frame.activeBorderColor"));
    Color INACTIVE_COLOR = (Color)Toolkit.getDefaultToolkit().getDesktopProperty("win.3d.shadowColor");
  }

  private class MyTopBorder implements Border {
    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      g.setColor(myIsActive ? MyTopBorderConsts.ACTIVE_COLOR : MyTopBorderConsts.INACTIVE_COLOR);
      RectanglePainter2D.FILL.paint((Graphics2D)g, x, y, width, MyTopBorderConsts.THICKNESS);
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return JBUI.insetsTop(MyTopBorderConsts.THICKNESS);
    }

    @Override
    public boolean isBorderOpaque() {
      return true;
    }
  }
}
