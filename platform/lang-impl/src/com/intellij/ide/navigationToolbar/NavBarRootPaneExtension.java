// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.navbar.ide.NavBarIdeUtil;
import com.intellij.ide.navbar.ide.NavBarService;
import com.intellij.ide.navigationToolbar.ui.NavBarUIManager;
import com.intellij.ide.ui.NavBarLocation;
import com.intellij.ide.ui.ToolbarSettings;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.ui.customization.CustomisedActionGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.StatusBarCentralWidgetProvider;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.openapi.wm.impl.status.InfoAndProgressPanel;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBThinOverlappingScrollBar;
import com.intellij.ui.hover.HoverListener;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

import static com.intellij.ide.navigationToolbar.NavBarRootPaneExtension.PANEL_KEY;

/**
 * @author Konstantin Bulenkov
 */
public final class NavBarRootPaneExtension implements IdeRootPaneNorthExtension {
  static final String PANEL_KEY = "NavBarPanel";

  @Override
  public @NotNull JComponent createComponent(@NotNull Project project, boolean isDocked) {
    return new MyNavBarWrapperPanel(project);
  }

  @Override
  public @NotNull String getKey() {
    return IdeStatusBarImpl.NAVBAR_WIDGET_KEY;
  }

  // used externally
  public abstract static class NavBarWrapperPanel extends JPanel implements UISettingsListener {
      public NavBarWrapperPanel(LayoutManager layout) {
        super(layout);
      }

      @Override
      protected Graphics getComponentGraphics(Graphics graphics) {
        return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
      }
    }
}

final class MyNavBarWrapperPanel extends NavBarRootPaneExtension.NavBarWrapperPanel implements
                                                                                    StatusBarCentralWidgetProvider {
  private final Project myProject;
  JComponent myNavigationBar;
  private JComponent myNavBarPanel;
  private JPanel myRunPanel;
  private Boolean myNavToolbarGroupExist;
  JScrollPane myScrollPane;

  MyNavBarWrapperPanel(Project project) {
    super(new BorderLayout());

    myProject = project;

    UISettings settings = UISettings.getInstance();
    if (!ExperimentalUI.isNewUI() || (settings.getShowNavigationBar() && settings.getNavBarLocation() == NavBarLocation.TOP)) {
      add(getNavBarPanel(), BorderLayout.CENTER);
    }
    else {
      setVisible(false);
    }

    putClientProperty(PANEL_KEY, myNavigationBar);
    revalidate();

    uiSettingsChanged(settings);
  }

  @NotNull
  @Override
  public JComponent createCentralStatusBarComponent() {
    return getNavBarPanel();
  }

  private JComponent getNavBarPanel() {
    if (myNavBarPanel != null) {
      return myNavBarPanel;
    }

    if (NavBarIdeUtil.isNavbarV2Enabled()) {
      myNavigationBar = NavBarService.getInstance(myProject).getStaticNavBarPanel();
    }
    else {
      myNavigationBar = new ReusableNavBarPanel(myProject, true);
      ((NavBarPanel)myNavigationBar).getModel().setFixedComponent(true);
    }
    myScrollPane = ScrollPaneFactory.createScrollPane(myNavigationBar);
    updateScrollBarFlippedState(null);

    myNavBarPanel = new NavBarContainer(new BorderLayout(), this);

    myNavBarPanel.add(myScrollPane, BorderLayout.CENTER);
    myNavBarPanel.setOpaque(!ExperimentalUI.isNewUI());
    myNavBarPanel.updateUI();

    if (ExperimentalUI.isNewNavbar()) {
      HoverListener hoverListener = new HoverListener() {
        @Override
        public void mouseEntered(@NotNull Component component, int x, int y) {
          toggleScrollBar(true);
        }

        @Override
        public void mouseMoved(@NotNull Component component, int x, int y) { }

        @Override
        public void mouseExited(@NotNull Component component) {
          toggleScrollBar(false);
        }
      };
      hoverListener.addTo(myNavBarPanel);
    }

    return myNavBarPanel;
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    toggleRunPanel(isShowToolPanel(uiSettings));
    toggleNavPanel(uiSettings);

    if (NavBarIdeUtil.isNavbarV2Enabled()) {
      NavBarService.getInstance(myProject).uiSettingsChanged(uiSettings);
    }

    if (myNavigationBar == null) {
      return;
    }

    if (myNavigationBar instanceof NavBarPanel) {
      ((NavBarPanel)myNavigationBar).updateState(uiSettings.getShowNavigationBar());
    }

    boolean visible = NavBarIdeUtil.isNavbarShown(uiSettings);
    if (ExperimentalUI.isNewUI()) {
      myScrollPane.setVisible(visible);
    }

    myNavigationBar.revalidate();
    setVisible(visible);

    revalidate();
    repaint();

    if (getComponentCount() > 0) {
      Component c = getComponent(0);
      if (c instanceof JComponent) {
        ((JComponent)c).setOpaque(false);
      }
    }
  }

  @Override
  public Insets getInsets() {
    return NavBarUIManager.getUI().getWrapperPanelInsets(super.getInsets());
  }

  private void updateScrollBarFlippedState(@Nullable NavBarLocation location) {
    if (ExperimentalUI.isNewNavbar() && myScrollPane != null) {
      if (location == null) location = UISettings.getInstance().getNavBarLocation();
      JBScrollPane.Flip flipState = (location == NavBarLocation.BOTTOM) ? JBScrollPane.Flip.VERTICAL : JBScrollPane.Flip.NONE;
      myScrollPane.putClientProperty(JBScrollPane.Flip.class, flipState);
    }
  }

  private static boolean isShowToolPanel(@NotNull UISettings uiSettings) {
    // Evanescent me: fix run panel show condition in ExpUI if necessary.
    if (!ExperimentalUI.isNewUI() && !uiSettings.getShowMainToolbar() && NavBarIdeUtil.isNavbarShown(uiSettings)) {
      ToolbarSettings toolbarSettings = ToolbarSettings.getInstance();
      return !toolbarSettings.isVisible() || !toolbarSettings.isAvailable();
    }
    return false;
  }

  private static void alignVertically(Container container) {
    if (container.getComponentCount() == 1) {
      Component c = container.getComponent(0);
      Insets insets = container.getInsets();
      Dimension d = c.getPreferredSize();
      Rectangle r = container.getBounds();
      c.setBounds(insets.left, (r.height - d.height - insets.top - insets.bottom) / 2 + insets.top, r.width - insets.left - insets.right,
                  d.height);
    }
  }

  private static boolean isNeedGap(final AnAction group) {
    final AnAction firstAction = getFirstAction(group);
    return firstAction instanceof ComboBoxAction;
  }

  private static @Nullable AnAction getFirstAction(final AnAction group) {
    if (group instanceof DefaultActionGroup) {
      AnAction firstAction = null;
      for (final AnAction action : ((DefaultActionGroup)group).getChildActionsOrStubs()) {
        if (action instanceof DefaultActionGroup) {
          firstAction = getFirstAction(action);
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
    if (group instanceof CustomisedActionGroup) {
      return ((CustomisedActionGroup)group).getFirstAction();
    }
    return null;
  }

  private boolean runToolbarExists() {
    if (myNavToolbarGroupExist == null) {
      final AnAction correctedAction = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_NAVBAR_TOOLBAR);
      myNavToolbarGroupExist =
        correctedAction instanceof DefaultActionGroup && ((DefaultActionGroup)correctedAction).getChildrenCount() > 0 ||
        correctedAction instanceof CustomisedActionGroup && ((CustomisedActionGroup)correctedAction).getFirstAction() != null;
    }
    return myNavToolbarGroupExist;
  }

  private void toggleNavPanel(UISettings settings) {
    boolean show = ExperimentalUI.isNewUI() ?
                   settings.getShowNavigationBar() && settings.getNavBarLocation() == NavBarLocation.TOP :
                   NavBarIdeUtil.isNavbarShown(settings);
    if (show) {
      ApplicationManager.getApplication().invokeLater(() -> {
        add(getNavBarPanel(), BorderLayout.CENTER);
        myNavBarPanel.updateUI();
      });
    }
    else {
      var c = ((BorderLayout)getLayout()).getLayoutComponent(BorderLayout.CENTER);
      if (c != null) {
        remove(c);
      }
    }

    updateScrollBarFlippedState(settings.getNavBarLocation());
    setVisible(show);
  }

  private void toggleRunPanel(boolean show) {
    CompletableFuture
      .supplyAsync(() -> CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_NAVBAR_TOOLBAR),
                   AppExecutorUtil.getAppExecutorService())
      .thenAcceptAsync(action -> {
        if (show && myRunPanel == null && runToolbarExists()) {
          if (myRunPanel != null) {
            remove(myRunPanel);
            myRunPanel = null;
          }

          ActionManager manager = ActionManager.getInstance();
          if (action instanceof ActionGroup) {
            ActionToolbar actionToolbar = manager.createActionToolbar(ActionPlaces.NAVIGATION_BAR_TOOLBAR, (ActionGroup)action, true);
            actionToolbar.setTargetComponent(null);
            myRunPanel = new JPanel(new BorderLayout()) {
              @Override
              public void doLayout() {
                alignVertically(this);
              }
            };
            myRunPanel.setOpaque(false);
            myRunPanel.add(actionToolbar.getComponent(), BorderLayout.CENTER);
            final boolean needGap = isNeedGap(action);
            myRunPanel.setBorder(JBUI.Borders.emptyLeft(needGap ? 5 : 1));
            NavBarLeftSideExtension.EP_NAME.forEachExtensionSafe(extension -> {
              extension.process(this, myProject);
            });
            add(myRunPanel, BorderLayout.EAST);
          }
        }
        else if (!show && myRunPanel != null) {
          remove(myRunPanel);
          myRunPanel = null;
        }
      }, command -> ApplicationManager.getApplication().invokeLater(command, myProject.getDisposed()));
  }

  void toggleScrollBar(boolean isOn) {
    JScrollBar scrollBar = myScrollPane.getHorizontalScrollBar();
    if (scrollBar instanceof JBScrollBar) {
      ((JBScrollBar)scrollBar).toggle(isOn);
    }
  }
}

final class NavBarContainer extends JPanel implements InfoAndProgressPanel.ScrollableToSelected {
  private final MyNavBarWrapperPanel panel;

  NavBarContainer(@NotNull LayoutManager layout, @NotNull MyNavBarWrapperPanel panel) {
    super(layout);

    this.panel = panel;
    updateUI();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    final Component navBar = panel.myScrollPane;
    Rectangle r = navBar.getBounds();

    Graphics2D g2d = (Graphics2D)g.create();
    g2d.translate(r.x, r.y);
    g2d.dispose();
  }

  @Override
  public void doLayout() {
    // align vertically
    final Rectangle r = getBounds();
    final Insets insets = getInsets();
    int x = insets.left;
    JScrollPane scrollPane = panel.myScrollPane;
    if (scrollPane == null || !scrollPane.isVisible()) {
      return;
    }

    int navBarHeight = ((Component)scrollPane).getPreferredSize().height;
    if (ExperimentalUI.isNewNavbar()) {
      navBarHeight = r.height;
    }

    scrollPane.setBounds(x, (r.height - navBarHeight) / 2, r.width - insets.left - insets.right, navBarHeight);
  }

  @Override
  public void updateUI() {
    // updateUI is called from JPanel constructor
    if (panel == null) {
      return;
    }

    super.updateUI();

    JScrollPane scrollPane = panel.myScrollPane;
    if (scrollPane == null || panel.myNavigationBar == null) {
      return;
    }

    var settings = UISettings.getInstance();
    var border = !ExperimentalUI.isNewUI() || settings.getShowNavigationBar()
                 ? new NavBarBorder()
                 : JBUI.Borders.empty();

    if (ExperimentalUI.isNewNavbar()) {
      scrollPane.setHorizontalScrollBar(new JBThinOverlappingScrollBar(Adjustable.HORIZONTAL));
      if (scrollPane instanceof JBScrollPane) {
        ((JBScrollPane)scrollPane).setOverlappingScrollBar(true);
      }
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      panel.toggleScrollBar(false);
    }
    else {
      scrollPane.setHorizontalScrollBar(null);
    }

    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(border);
    scrollPane.setOpaque(false);
    scrollPane.getViewport().setOpaque(false);
    scrollPane.setViewportBorder(null);

    if (ExperimentalUI.isNewUI()) {
      boolean visible = NavBarIdeUtil.isNavbarShown(settings);
      scrollPane.setVisible(visible);
      if (panel.myNavigationBar instanceof NavBarPanel) {
        ((NavBarPanel)panel.myNavigationBar).updateState(visible);
      }
    }
    panel.myNavigationBar.setBorder(null);
  }

  @Override
  public void updateAutoscrollLimit(InfoAndProgressPanel.AutoscrollLimit limit) {
    JComponent navigationBar = panel.myNavigationBar;
    if (navigationBar instanceof NavBarPanel) {
      ((NavBarPanel)navigationBar).updateAutoscrollLimit(limit);
    }
  }
}