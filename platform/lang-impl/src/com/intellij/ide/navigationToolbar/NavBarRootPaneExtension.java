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

import com.intellij.ide.navigationToolbar.ui.NavBarUIManager;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class NavBarRootPaneExtension extends IdeRootPaneNorthExtension {
  private JComponent myWrapperPanel;
  @NonNls public static final String NAV_BAR = "NavBar";
  private Project myProject;
  private NavBarPanel myNavigationBar;
  private JPanel myRunPanel;
  private boolean myNavToolbarGroupExist;
  private JScrollPane myScrollPane;

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

    Disposer.register(myProject, this);
  }

  @Override
  public IdeRootPaneNorthExtension copy() {
    return new NavBarRootPaneExtension(myProject);
  }

  public boolean isMainToolbarVisible() {
    return UISettings.getInstance().SHOW_MAIN_TOOLBAR || !myNavToolbarGroupExist;
  }

  public static boolean runToolbarExists() {
    final AnAction navBarToolBar = ActionManager.getInstance().getAction("NavBarToolBar");
    return navBarToolBar instanceof DefaultActionGroup && ((DefaultActionGroup)navBarToolBar).getChildrenCount() > 0;
  }

  public JComponent getComponent() {
    if (myWrapperPanel == null) {
      myWrapperPanel = new JPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          NavBarUIManager.getUI().doPaintWrapperPanel((Graphics2D)g, getBounds(), isMainToolbarVisible());
        }

        @Override
        public Insets getInsets() {
          return NavBarUIManager.getUI().getWrapperPanelInsets(super.getInsets());
        }
      };
      myWrapperPanel.add(buildNavBarPanel(), BorderLayout.CENTER);
      toggleRunPanel(!UISettings.getInstance().SHOW_MAIN_TOOLBAR);
    }

    return myWrapperPanel;
  }

  private static void alignVertically(Container container) {
    if (container.getComponentCount() == 1) {
      Component c = container.getComponent(0);
      Insets insets = container.getInsets();
      Dimension d = c.getPreferredSize();
      Rectangle r = container.getBounds();
      c.setBounds(insets.left, (r.height - d.height) / 2, r.width - insets.left - insets.right, d.height);
    }
  }

  private void toggleRunPanel(final boolean show) {
    if (show && myRunPanel == null && runToolbarExists()) {
      final ActionManager manager = ActionManager.getInstance();
      final AnAction toolbarRunGroup = manager.getAction("NavBarToolBar");
      if (toolbarRunGroup instanceof DefaultActionGroup) {
        final DefaultActionGroup group = (DefaultActionGroup)toolbarRunGroup;
        final boolean needGap = isNeedGap(group);
        final ActionToolbar actionToolbar = manager.createActionToolbar(ActionPlaces.NAVIGATION_BAR, group, true);
        final JComponent component = actionToolbar.getComponent();
        component.setOpaque(false);
        myRunPanel = new JPanel(new BorderLayout()) {
          @Override
          public void doLayout() {
            alignVertically(this);
          }
        };
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
        if (myNavigationBar != null && !Disposer.isDisposed(myNavigationBar)) {
          Disposer.dispose(myNavigationBar);
        }
        myNavigationBar = new NavBarPanel(myProject);
        myWrapperPanel.putClientProperty("NavBarPanel", myNavigationBar);
        myNavigationBar.getModel().setFixedComponent(true);

        myScrollPane = ScrollPaneFactory.createScrollPane(myNavigationBar);
        myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        myScrollPane.setHorizontalScrollBar(null);
        myScrollPane.setBorder(new NavBarBorder(true, 0));
        myScrollPane.setOpaque(false);
        myScrollPane.getViewport().setOpaque(false);
        panel.get().setOpaque(true);
        //panel.get().setBorder();
        myNavigationBar.setBorder(null);
        panel.get().add(myScrollPane, BorderLayout.CENTER);
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
        final Component navBar = myScrollPane;
        Insets insets = getInsets();
        Rectangle r = navBar.getBounds();

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.translate(r.x, r.y);

        Rectangle rectangle =
          new Rectangle(0, 0, r.width + insets.left + insets.right, r.height + insets.top + insets.bottom);
        NavBarUIManager.getUI().doPaintNavBarPanel(g2d, rectangle, isMainToolbarVisible(), isUndocked());
        
        g2d.dispose();
      }

      @Override
      public void doLayout() {
        // align vertically
        final Rectangle r = getBounds();
        final Insets insets = getInsets();
        int x = insets.left;
        if (myScrollPane == null) return;
        final Component navBar = myScrollPane;

        final Dimension preferredSize = navBar.getPreferredSize();

        navBar.setBounds(x, (r.height - preferredSize.height) / 2,
                         r.width - insets.left - insets.right, preferredSize.height);
      }
    });

    updater.run();
    return panel.get();
  }

  public void uiSettingsChanged(final UISettings settings) {
    if (myNavigationBar != null) {
      myNavigationBar.updateState(settings.SHOW_NAVIGATION_BAR);
      myWrapperPanel.setVisible(settings.SHOW_NAVIGATION_BAR);
      
      myWrapperPanel.revalidate();
      myNavigationBar.revalidate();
      myWrapperPanel.repaint();

      if (myWrapperPanel.getComponentCount() > 0) {
        final Component c = myWrapperPanel.getComponent(0);
        if (c instanceof JComponent) ((JComponent)c).setOpaque(false);
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
    myProject = null;
  }
}
