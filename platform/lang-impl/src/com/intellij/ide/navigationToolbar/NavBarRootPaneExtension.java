/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 12-Nov-2007
 */
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
//TODO[kb]: cleanup
public class NavBarRootPaneExtension extends IdeRootPaneNorthExtension {
  private static final Icon CROSS_ICON = IconLoader.getIcon("/actions/cross.png");

  private JComponent myWrapperPanel;
  @NonNls public static final String NAV_BAR = "NavBar";
  private final Project myProject;
  private NavBarPanel myNavigationBar;
  private JPanel myRunPanel;
  private boolean myNavToolbarGroupExist;
  private JScrollPane myScrollPane;
  private JLabel myCloseIcon;

  public NavBarRootPaneExtension(Project project) {
    myProject = project;

    UISettings.getInstance().addUISettingsListener(new UISettingsListener() {
      @Override
      public void uiSettingsChanged(UISettings source) {
        toggleRunPanel(!source.SHOW_MAIN_TOOLBAR);
      }
    }, this);

    final AnAction navBarToolBar = ActionManager.getInstance().getAction("NavBarToolBar");
    myNavToolbarGroupExist = navBarToolBar instanceof DefaultActionGroup && ((DefaultActionGroup)navBarToolBar).getChildrenCount() > 0;
  }

  @Override
  public IdeRootPaneNorthExtension copy() {
    return new NavBarRootPaneExtension(myProject);
  }

  public boolean isMainToolbarVisible() {
    return UISettings.getInstance().SHOW_MAIN_TOOLBAR || !myNavToolbarGroupExist;
  }

  private static boolean runToolbarExists() {
    final AnAction navBarToolBar = ActionManager.getInstance().getAction("NavBarToolBar");
    return navBarToolBar instanceof DefaultActionGroup && ((DefaultActionGroup)navBarToolBar).getChildrenCount() > 0;
  }

  public JComponent getComponent() {
    if (myWrapperPanel == null) {
      myWrapperPanel = new JPanel(new BorderLayout()) {
        @Override
        protected void paintChildren(Graphics g) {
          super.paintChildren(g);
          if (UIUtil.isUnderAquaLookAndFeel() && !isMainToolbarVisible()) {
            final Rectangle r = getBounds();
            //g.setColor(new Color(0,0,0, 90));
            //g.drawLine(0, r.height - 4, r.width, r.height - 4);
              g.setColor(new Color(0, 0, 0, 90));
              g.drawLine(0, r.height - 2, r.width, r.height - 2);
              g.setColor(new Color(0, 0, 0, 20));
              g.drawLine(0, r.height - 1, r.width, r.height - 1);
          }
        }

        @Override
        protected void paintComponent(Graphics g) {
          //if (!UIUtil.isUnderAquaLookAndFeel()) {
          //  super.paintComponent(g);
          //  return;
          //}

          final Rectangle r = getBounds();
          if (isMainToolbarVisible()) {
            g.setColor(new Color(200, 200, 200));
            g.fillRect(0, 0, r.width, r.height);
          }
          else {
            final Color startColor = UIUtil.isUnderAquaLookAndFeel() ? new Color(240, 240, 240) : UIUtil.getControlColor();
            final Color endColor = ColorUtil.shift(startColor, 7.0d / 8.0d);
            ((Graphics2D)g).setPaint(new GradientPaint(0, 0, startColor, 0, r.height, endColor));
            g.fillRect(0, 0, r.width, r.height);
            //UIUtil.drawGradientHToolbarBackground(g, r.width, r.height);
          }
        }

        @Override
        public Insets getInsets() {
          final Insets i = super.getInsets();
          if (!UIUtil.isUnderAquaLookAndFeel()) {
            return new Insets(0, 0, 0, 0);
          }

          return new Insets(i.top, i.left, i.bottom + 1, i.right);
        }
      };
      myWrapperPanel.add(buildNavBarPanel(), BorderLayout.CENTER);
      toggleRunPanel(!UISettings.getInstance().SHOW_MAIN_TOOLBAR);
    }

    return myWrapperPanel;
  }

  private void toggleRunPanel(final boolean show) {
    if (show && myRunPanel == null && runToolbarExists()) {
      final ActionManager manager = ActionManager.getInstance();
      final AnAction toolbarRunGroup = manager.getAction("NavBarToolBar");
      if (toolbarRunGroup instanceof DefaultActionGroup) {
        final DefaultActionGroup group = (DefaultActionGroup)toolbarRunGroup;
        final boolean needGap = isNeedGap(group);
        final ActionToolbar actionToolbar = manager.createActionToolbar(ActionPlaces.NAVIGATION_BAR, group, true);
        //actionToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
        final JComponent component = actionToolbar.getComponent();
        component.setOpaque(false);
        myRunPanel = new JPanel(new BorderLayout());
        myRunPanel.setOpaque(false);
        myRunPanel.add(component, BorderLayout.CENTER);

        myRunPanel.setBorder(BorderFactory.createEmptyBorder(0, needGap ? 5 : 1, 0, 0));
        myWrapperPanel.add(myRunPanel, BorderLayout.EAST);
      }
    }
    else if (!show && myRunPanel != null) {
      myWrapperPanel.remove(myRunPanel);
      myRunPanel = null;
    }
  }

  private boolean isUndocked() {
    final Window ancestor = SwingUtilities.getWindowAncestor(myWrapperPanel);
    return (ancestor != null && !(ancestor instanceof IdeFrameImpl)) || !UISettings.getInstance().SHOW_MAIN_TOOLBAR;
  }

  private static boolean isNeedGap(final DefaultActionGroup group) {
    final AnAction firstAction = getFirstAction(group);
    return firstAction instanceof ComboBoxAction;
  }

  @Nullable
  private static AnAction getFirstAction(final DefaultActionGroup group) {
    AnAction firstAction = null;
    for (final AnAction action : group.getChildActionsOrStubs()) {
      if (action instanceof DefaultActionGroup) {
        firstAction = getFirstAction((DefaultActionGroup)action);
      }
      else if (action instanceof Separator || action instanceof ActionGroup) {
        continue;
      }
      else {
        firstAction = action;
        break;
      }

      if (firstAction != null) break;
    }

    return firstAction;
  }

  private JComponent buildNavBarPanel() {
    final Ref<JPanel> panel = new Ref<JPanel>(null);
    final Runnable updater = new Runnable() {
      String laf;

      @Override
      public void run() {
        if (LafManager.getInstance().getCurrentLookAndFeel().getName().equals(laf)) return;
        laf = LafManager.getInstance().getCurrentLookAndFeel().getName();
        panel.get().removeAll();
        myScrollPane = null;
        myCloseIcon = null;
        if (myNavigationBar != null && !Disposer.isDisposed(myNavigationBar)) {
          Disposer.dispose(myNavigationBar);
        }
        myNavigationBar = new NavBarPanel(myProject);
        myWrapperPanel.putClientProperty("NavBarPanel", myNavigationBar);
        myNavigationBar.getModel().setFixedComponent(true);

        myScrollPane = ScrollPaneFactory.createScrollPane(myNavigationBar);
        myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        myScrollPane.setHorizontalScrollBar(null);
        myScrollPane.setBorder(null);

        myScrollPane.setOpaque(false);
        myScrollPane.getViewport().setOpaque(false);

        //panel.get().setBackground(UIUtil.isUnderGTKLookAndFeel() ? Color.WHITE : UIUtil.getListBackground());
        panel.get().setOpaque(true);//!UIUtil.isUnderAquaLookAndFeel() || UISettings.getInstance().SHOW_MAIN_TOOLBAR);
        panel.get().setBorder(new NavBarBorder(true, 0));
        myNavigationBar.setBorder(null);
        panel.get().add(myScrollPane, BorderLayout.CENTER);
        //if (!SystemInfo.isMac) {
        //  myCloseIcon = new JLabel(CROSS_ICON);
        //  myCloseIcon.addMouseListener(new MouseAdapter() {
        //    public void mouseClicked(final MouseEvent e) {
        //      UISettings.getInstance().SHOW_NAVIGATION_BAR = false;
        //      uiSettingsChanged(UISettings.getInstance());
        //    }
        //  });
        //  panel.get().add(myCloseIcon, BorderLayout.EAST);
        //}
      }
    };

    panel.set(new JPanel(new BorderLayout()) {
      @Override
      public void updateUI() {
        super.updateUI();
        if (UISettings.getInstance().SHOW_NAVIGATION_BAR) {
          SwingUtilities.invokeLater(updater);
        }
      }

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        //if (UIUtil.isUnderAquaLookAndFeel()) {
        final Rectangle r = getBounds();
        final Graphics2D g2d = (Graphics2D)g;
        //if (!isMainToolbarVisible() && UIUtil.isUnderAquaLookAndFeel()) {
          //if (UIUtil.isUnderAquaLookAndFeel()) {
          //  final Dimension d = getPreferredSize();
          //  final int topOffset = UIUtil.isUnderAquaLookAndFeel() ? (r.height - d.height) / 2 + 2 : 0;
          //  UIUtil.drawDoubleSpaceDottedLine(g2d, topOffset, topOffset + d.height - 1, r.width - 1, Color.GRAY, false);
          //} else {
          //  g2d.setPaint(getBackground());
          //  g2d.fillRect(0,0, r.width, r.height);
          //}
        //}
        //else {
          final boolean undocked = isUndocked();
          final Color startColor = UIUtil.isUnderAquaLookAndFeel() ? new Color(240, 240, 240) : UIUtil.getControlColor();
          final Color endColor = ColorUtil.shift(startColor, 7.0d / 8.0d);
          g2d.setPaint(new GradientPaint(0, 0, startColor, 0, r.height, endColor));
          g.fillRect(0, 0, r.width, r.height);

          if (!undocked) {
            g.setColor(new Color(255, 255, 255, 220));
            g.drawLine(0, 1, r.width, 1);
          }

          g.setColor(UIUtil.getBorderColor());
          if (!undocked) g.drawLine(0, 0, r.width, 0);
          g.drawLine(0, r.height-1, r.width, r.height-1);
          
          if (!isMainToolbarVisible()) {
            UIUtil.drawDottedLine(g2d, r.width - 1, 0, r.width - 1, r.height, null, Color.GRAY);
          }
        //}
        //} else {
        //  super.paintComponent(g);
        //}
      }

      @Override
      public void doLayout() {
        // align vertically
        final Rectangle r = getBounds();
        final Insets insets = getInsets();
        int x = insets.left;
        if (myScrollPane == null) return;
        final Component navBar = myScrollPane;
        final Component closeLabel = myCloseIcon;

        final Dimension preferredSize = navBar.getPreferredSize();
        final Dimension closePreferredSize = closeLabel == null ? new Dimension() : closeLabel.getPreferredSize();

        navBar.setBounds(x, insets.top + ((r.height - preferredSize.height - insets.top - insets.bottom) / 2),
                         r.width - insets.left - insets.right - closePreferredSize.width, preferredSize.height);

        if (closeLabel != null) {
          closeLabel.setBounds(x + r.width - insets.left - insets.right - closePreferredSize.width,
                               insets.top + ((r.height - closePreferredSize.height - insets.top - insets.bottom) / 2),
                               closePreferredSize.width, closePreferredSize.height);
        }
      }
    });

    updater.run();
    final JPanel main = new JPanel(new BorderLayout());
    main.setOpaque(false);
    main.add(panel.get(), BorderLayout.NORTH);
    return main;
  }

  public void uiSettingsChanged(final UISettings settings) {
    if (myNavigationBar != null) {
      myNavigationBar.updateState(settings.SHOW_NAVIGATION_BAR);
      myWrapperPanel.setVisible(settings.SHOW_NAVIGATION_BAR);

      if (myWrapperPanel.getComponentCount() > 0) {
        final Component c = myWrapperPanel.getComponent(0);
        if (c instanceof JComponent) ((JComponent)c).setOpaque(false);
        //!UIUtil.isUnderAquaLookAndFeel() || isMainToolbarVisible());
      }
    }
  }

  @NonNls
  public String getKey() {
    return NAV_BAR;
  }


  public void dispose() {
    myWrapperPanel.setVisible(false);
    myWrapperPanel = null;
    myRunPanel = null;
    myNavigationBar = null;
  }
}
