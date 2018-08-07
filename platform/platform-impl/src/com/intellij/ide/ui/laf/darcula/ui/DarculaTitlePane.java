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

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.DarculaTitleButtons;
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.HelpAction;
import com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.ResizableDarculaTitleButtons;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.wm.impl.IdeMenuBar;
import com.intellij.openapi.wm.impl.IdeRootPane;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTitlePane extends JPanel {
  private static final Icon mySystemIcon = AllIcons.Icon_small;

  private PropertyChangeListener myPropertyChangeListener;
  private JMenuBar myMenuBar;
  private JMenuBar myIdeMenu;
  private Action myCloseAction;
  private Action myIconifyAction;
  private Action myRestoreAction;
  private Action myMaximizeAction;
  private HelpAction myHelpAction;
  private WindowListener myWindowListener;
  private Window myWindow;
  private final JRootPane myRootPane;
  private int myState;
  private final DarculaRootPaneUI rootPaneUI;

  private DarculaTitleButtons buttonPanes;
  private final JLabel titleLabel = new JLabel();

  private final Color myInactiveBackground = UIManager.getColor("inactiveCaption");
  private final Color myInactiveForeground = UIManager.getColor("inactiveCaptionText");
  private final Color myInactiveShadow = UIManager.getColor("inactiveCaptionBorder");
  private Color myActiveBackground = null;
  private Color myActiveForeground = null;
  private Color myActiveShadow = null;

  public DarculaTitlePane(JRootPane root, DarculaRootPaneUI ui) {
    this.myRootPane = root;
    rootPaneUI = ui;

    myState = -1;

    installSubcomponents();
    determineColors();
    installDefaults();

    setOpaque(true);
    setBackground(JBUI.CurrentTheme.CustomFrameDecorations.titlePaneBackground());
    setBorder(new DarculaMenuBarBorder(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()));
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
      myPropertyChangeListener = createWindowPropertyChangeListener();
      myWindow.addPropertyChangeListener(myPropertyChangeListener);
    }
  }

  private void uninstallListeners() {
    if (myWindow != null) {
      myWindow.removeWindowListener(myWindowListener);
      myWindow.removePropertyChangeListener(myPropertyChangeListener);
    }
  }

  private WindowListener createWindowListener() {
    return new WindowHandler();
  }

  private PropertyChangeListener createWindowPropertyChangeListener() {
    return new PropertyChangeHandler();
  }

  public JRootPane getRootPane() {
    return myRootPane;
  }

  private int getWindowDecorationStyle() {
    return getRootPane().getWindowDecorationStyle();
  }

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
  }

  public void removeNotify() {
    super.removeNotify();

    uninstallListeners();
    myWindow = null;
  }

  private void installSubcomponents() {
    int decorationStyle = getWindowDecorationStyle();
    if (decorationStyle == JRootPane.FRAME) {
      setLayout(new MigLayout("fill, ins 0, gap 0", JBUI.scale(7)+"[pref!]"+JBUI.scale(9)+"[pref!]push[pref!]"));

      createActions();
      buttonPanes = ResizableDarculaTitleButtons.Companion.create(myCloseAction,
                                                                  myRestoreAction, myIconifyAction,
                                                                  myMaximizeAction, myHelpAction);
      myMenuBar = createMenuBar();
      add(myMenuBar);
      if (myRootPane instanceof IdeRootPane) {
        myIdeMenu = new IdeMenuBar(ActionManagerEx.getInstanceEx(), DataManager.getInstance());
        add(myIdeMenu);
      } else {
        add(titleLabel, "growx");
      }
      add(buttonPanes.getView());
    }
    else if (decorationStyle == JRootPane.PLAIN_DIALOG ||
             decorationStyle == JRootPane.INFORMATION_DIALOG ||
             decorationStyle == JRootPane.ERROR_DIALOG ||
             decorationStyle == JRootPane.COLOR_CHOOSER_DIALOG ||
             decorationStyle == JRootPane.FILE_CHOOSER_DIALOG ||
             decorationStyle == JRootPane.QUESTION_DIALOG ||
             decorationStyle == JRootPane.WARNING_DIALOG) {
      setLayout(new MigLayout("fill, ins 0, gap 0", JBUI.scale(7)+"[pref!]push[pref!]"));

      createActions();
      // titleLabel.setIcon(mySystemIcon);
      add(titleLabel, "growx");
      buttonPanes = DarculaTitleButtons.Companion.create(myCloseAction, myHelpAction);
      add(buttonPanes.getView());
    }
  }

  private void determineColors() {
    switch (getWindowDecorationStyle()) {
      case JRootPane.FRAME:
        myActiveBackground = UIManager.getColor("activeCaption");
        myActiveForeground = UIManager.getColor("activeCaptionText");
        myActiveShadow = UIManager.getColor("activeCaptionBorder");
        break;
      case JRootPane.ERROR_DIALOG:
        myActiveBackground = new Color(43, 43, 43);//UIManager.getColor("OptionPane.errorDialog.titlePane.background");
        myActiveForeground = UIManager.getColor("OptionPane.errorDialog.titlePane.foreground");
        myActiveShadow = UIManager.getColor("OptionPane.errorDialog.titlePane.shadow");
        break;
      case JRootPane.QUESTION_DIALOG:
      case JRootPane.COLOR_CHOOSER_DIALOG:
      case JRootPane.FILE_CHOOSER_DIALOG:
        myActiveBackground = new Color(43, 43, 43);//UIManager.getColor("OptionPane.questionDialog.titlePane.background");
        myActiveForeground = UIManager.getColor("OptionPane.questionDialog.titlePane.foreground");
        myActiveShadow = UIManager.getColor("OptionPane.questionDialog.titlePane.shadow");
        break;
      case JRootPane.WARNING_DIALOG:
        myActiveBackground = new Color(43, 43, 43);//UIManager.getColor("OptionPane.warningDialog.titlePane.background");
        myActiveForeground = UIManager.getColor("OptionPane.warningDialog.titlePane.foreground");
        myActiveShadow = UIManager.getColor("OptionPane.warningDialog.titlePane.shadow");
        break;
      case JRootPane.PLAIN_DIALOG:
      case JRootPane.INFORMATION_DIALOG:
      default:
        myActiveBackground = new Color(43, 43, 43);//UIManager.getColor("activeCaption");
        myActiveForeground = UIManager.getColor("activeCaptionText");
        myActiveShadow = UIManager.getColor("activeCaptionBorder");
        break;
    }

    myActiveBackground = new Color(43, 43, 43);//UIManager.getColor("activeCaption");
    myActiveForeground = JBColor.foreground();//UIManager.getColor("activeCaptionText");
    myActiveShadow = UIManager.getColor("activeCaptionBorder");
  }

  private void installDefaults() {
    setFont(JBUI.Fonts.label().asBold());
  }


  protected JMenuBar createMenuBar() {
    myMenuBar = new JMenuBar(){
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(mySystemIcon.getIconWidth(), mySystemIcon.getIconHeight());
      }

      @Override
      public void paint(Graphics g) {
        mySystemIcon.paintIcon(this, g, 0, 0);
      }
    };

    JMenu menu = new JMenu();
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
    myHelpAction = new HelpAction(this);

    if (getWindowDecorationStyle() == JRootPane.FRAME) {
      myIconifyAction = new IconifyAction();
      myRestoreAction = new RestoreAction();
      myMaximizeAction = new MaximizeAction();
    }
  }

  private JMenu createMenu() {
    JMenu menu = new JMenu("");

    if (getWindowDecorationStyle() == JRootPane.FRAME) {
      addMenuItems(menu);
    }
    return menu;
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
        JRootPane rootPane = getRootPane();

        if (((state & Frame.MAXIMIZED_BOTH) != 0) &&
            (rootPane.getBorder() == null ||
             (rootPane.getBorder() instanceof UIResource)) &&
            frame.isShowing()) {
          rootPane.setBorder(null);
        }
        else if ((state & Frame.MAXIMIZED_BOTH) == 0) {
          // This is a croak, if state becomes bound, this can
          // be nuked.
          rootPaneUI.installBorder(rootPane);
        }
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
    public CloseAction() {
      super("Close", AllIcons.Windows.CloseSmall);
    }

    public void actionPerformed(ActionEvent e) {
      close();
    }
  }


  private class IconifyAction extends AbstractAction {
    public IconifyAction() {
      super("Minimize", AllIcons.Windows.MinimizeSmall);
    }

    public void actionPerformed(ActionEvent e) {
      iconify();
    }
  }


  private class RestoreAction extends AbstractAction {
    public RestoreAction() {
      super("Restore", AllIcons.Windows.RestoreSmall);
    }

    public void actionPerformed(ActionEvent e) {
      restore();
    }
  }


  private class MaximizeAction extends AbstractAction {
    public MaximizeAction() {
      super("Maximize", AllIcons.Windows.MaximizeSmall);
    }

    public void actionPerformed(ActionEvent e) {
      maximize();
    }
  }

  private class PropertyChangeHandler implements PropertyChangeListener {
    public void propertyChange(PropertyChangeEvent pce) {
      String name = pce.getPropertyName();

      if ("resizable".equals(name) || "state".equals(name)) {
        Frame frame = getFrame();

        if (frame != null) {
          setState(frame.getExtendedState(), true);
        }
        if ("resizable".equals(name)) {
          getRootPane().repaint();
        }
      }
      else if ("title".equals(name)) {
        repaint();
      }
      else if ("componentOrientation" == name) {
        revalidate();
        repaint();
      }
    }
  }

  private class WindowHandler extends WindowAdapter {
    public void windowActivated(WindowEvent ev) {
      setActive(true);
    }

    public void windowDeactivated(WindowEvent ev) {
      setActive(false);
    }
  }
}
