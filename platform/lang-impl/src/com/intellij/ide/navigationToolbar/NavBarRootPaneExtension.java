// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.navbar.ide.NavBarService;
import com.intellij.ide.navbar.ide.NavigationBarKt;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarCentralWidget;
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

/**
 * @author Konstantin Bulenkov
 */
public final class NavBarRootPaneExtension extends IdeRootPaneNorthExtension implements StatusBarCentralWidget {
  private static final Logger LOG = Logger.getInstance(NavBarRootPaneExtension.class);

  static final String PANEL_KEY = "NavBarPanel";

  private final Project myProject;
  private JComponent myWrapperPanel;
  private JComponent myNavigationBar;
  private JComponent myNavBarPanel;
  private JPanel myRunPanel;
  private Boolean myNavToolbarGroupExist;
  private JScrollPane myScrollPane;

  public NavBarRootPaneExtension(@NotNull Project project) {
    myProject = project;

    myProject.getMessageBus().connect().subscribe(UISettingsListener.TOPIC, uiSettings -> {
      toggleRunPanel(isShowToolPanel(uiSettings));
      toggleNavPanel(uiSettings);
    });

  }

  @Override
  public void revalidate() {
    boolean showToolPanel = isShowToolPanel(UISettings.getInstance());
    LOG.debug("Revalidate in the navbarRootPane, toolbar visible: " + showToolPanel);
    if (showToolPanel) {
      toggleRunPanel(true);
    }
  }

  @Override
  public IdeRootPaneNorthExtension copy() {
    return new NavBarRootPaneExtension(myProject);
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

  @Override
  public @NotNull JComponent getComponent() {
    if (myWrapperPanel == null) {
      myWrapperPanel = new NavBarWrapperPanel(new BorderLayout()) {
        @Override
        public Insets getInsets() {
          return NavBarUIManager.getUI().getWrapperPanelInsets(super.getInsets());
        }
      };

      UISettings settings = UISettings.getInstance();
      if (!ExperimentalUI.isNewUI() || settings.getShowNavigationBar() && settings.getNavBarLocation() == NavBarLocation.TOP) {
        myWrapperPanel.add(getNavBarPanel(), BorderLayout.CENTER);
      }
      else {
        myWrapperPanel.setVisible(false);
      }

      getNavBarPanel(); //init myNavigationBar
      myWrapperPanel.putClientProperty(PANEL_KEY, myNavigationBar);
      revalidate();
    }
    return myWrapperPanel;
  }

  private void toggleNavPanel(UISettings settings) {
    boolean show = ExperimentalUI.isNewUI() ?
                   settings.getShowNavigationBar() && settings.getNavBarLocation() == NavBarLocation.TOP :
                   settings.getShowNavigationBar() && !settings.getPresentationMode();
    if (show) {
      ApplicationManager.getApplication().invokeLater(() -> {
        myWrapperPanel.add(getNavBarPanel(), BorderLayout.CENTER);
        myNavBarPanel.updateUI();
      });
    }
    else {
      var c = ((BorderLayout)myWrapperPanel.getLayout()).getLayoutComponent(BorderLayout.CENTER);
      if (c != null) myWrapperPanel.remove(c);
    }

    updateScrollBarFlippedState(settings.getNavBarLocation());
    myWrapperPanel.setVisible(show);
  }

  private void toggleRunPanel(boolean show) {
    CompletableFuture
      .supplyAsync(() -> CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_NAVBAR_TOOLBAR),
                   AppExecutorUtil.getAppExecutorService())
      .thenAcceptAsync(action -> {
        if (show && myRunPanel == null && runToolbarExists()) {
          if (myWrapperPanel != null && myRunPanel != null) {
            myWrapperPanel.remove(myRunPanel);
            myRunPanel = null;
          }

          ActionManager manager = ActionManager.getInstance();
          if (action instanceof ActionGroup && myWrapperPanel != null) {
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
              extension.process(myWrapperPanel, myProject);
            });
            myWrapperPanel.add(myRunPanel, BorderLayout.EAST);
          }
        }
        else if (!show && myRunPanel != null) {
          myWrapperPanel.remove(myRunPanel);
          myRunPanel = null;
        }
      }, command -> ApplicationManager.getApplication().invokeLater(command, myProject.getDisposed()));
  }

  private class NavBarContainer extends JPanel implements InfoAndProgressPanel.ScrollableToSelected {
    NavBarContainer(LayoutManager layout) {
      super(layout);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      final Component navBar = myScrollPane;
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
      if (myScrollPane == null || !myScrollPane.isVisible()) return;
      final Component navBar = myScrollPane;

      final int preferredHeight = navBar.getPreferredSize().height;

      int navBarHeight = preferredHeight;
      if (ExperimentalUI.isNewNavbar()) {
        navBarHeight = r.height;
      }

      navBar.setBounds(x, (r.height - navBarHeight) / 2,
                       r.width - insets.left - insets.right, navBarHeight);
    }

    @Override
    public void updateUI() {
      super.updateUI();
      if (myScrollPane == null || myNavigationBar == null) return;

      var settings = UISettings.getInstance();
      var border = !ExperimentalUI.isNewUI() || settings.getShowNavigationBar()
                   ? new NavBarBorder()
                   : JBUI.Borders.empty();

      if (ExperimentalUI.isNewNavbar()) {
        myScrollPane.setHorizontalScrollBar(new JBThinOverlappingScrollBar(Adjustable.HORIZONTAL));
        if (myScrollPane instanceof JBScrollPane) {
          ((JBScrollPane) myScrollPane).setOverlappingScrollBar(true);
        }
        myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        toggleScrollBar(false);
      }
      else {
        myScrollPane.setHorizontalScrollBar(null);
      }

      myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
      myScrollPane.setBorder(border);
      myScrollPane.setOpaque(false);
      myScrollPane.getViewport().setOpaque(false);
      myScrollPane.setViewportBorder(null);

      if (ExperimentalUI.isNewUI()) {
        boolean visible = settings.getShowNavigationBar() && !settings.getPresentationMode();
        myScrollPane.setVisible(visible);
        if (myNavigationBar instanceof NavBarPanel) {
          ((NavBarPanel)myNavigationBar).updateState(visible);
        }
      }
      myNavigationBar.setBorder(null);
    }

    @Override
    public void updateAutoscrollLimit(InfoAndProgressPanel.AutoscrollLimit limit) {
      if (myNavigationBar != null && myNavigationBar instanceof NavBarPanel) {
        ((NavBarPanel)myNavigationBar).updateAutoscrollLimit(limit);
      }
    }
  }

  private JComponent getNavBarPanel() {
    if (myNavBarPanel != null) return myNavBarPanel;

    if (NavigationBarKt.getNavbarV2Enabled()) {
      myNavigationBar = myProject.getService(NavBarService.class).getStaticNavbarPanel();
    }
    else {
      myNavigationBar = new ReusableNavBarPanel(myProject, true);
      ((NavBarPanel)myNavigationBar).getModel().setFixedComponent(true);
    }
    myScrollPane = ScrollPaneFactory.createScrollPane(myNavigationBar);
    updateScrollBarFlippedState(null);

    myNavBarPanel = new NavBarContainer(new BorderLayout());

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

  private void toggleScrollBar(boolean isOn) {
    JScrollBar scrollBar = myScrollPane.getHorizontalScrollBar();
    if (scrollBar instanceof JBScrollBar) ((JBScrollBar)scrollBar).toggle(isOn);
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings settings) {

    if (NavigationBarKt.getNavbarV2Enabled()) {
      myProject.getService(NavBarService.class).uiSettingsChanged(settings);
    }

    if (myNavigationBar == null) {
      return;
    }

    if (myNavigationBar instanceof NavBarPanel) {
      ((NavBarPanel)myNavigationBar).updateState(settings.getShowNavigationBar());
    }
    boolean visible = settings.getShowNavigationBar() && !settings.getPresentationMode();
    if (ExperimentalUI.isNewUI()) {
      myScrollPane.setVisible(visible);
    }

    myNavigationBar.revalidate();
    if (myWrapperPanel != null) {
      myWrapperPanel.setVisible(visible);

      myWrapperPanel.revalidate();
      myWrapperPanel.repaint();

      if (myWrapperPanel.getComponentCount() > 0) {
        Component c = myWrapperPanel.getComponent(0);
        if (c instanceof JComponent) {
          ((JComponent)c).setOpaque(false);
        }
      }
    }
  }

  private void updateScrollBarFlippedState(@Nullable NavBarLocation location) {
    if (ExperimentalUI.isNewNavbar() && myScrollPane != null) {
      if (location == null) location = UISettings.getInstance().getNavBarLocation();
      JBScrollPane.Flip flipState = (location == NavBarLocation.BOTTOM) ? JBScrollPane.Flip.VERTICAL : JBScrollPane.Flip.NONE;
      myScrollPane.putClientProperty(JBScrollPane.Flip.class, flipState);
    }
  }

  @Override
  public @NotNull String getKey() {
    return IdeStatusBarImpl.NAVBAR_WIDGET_KEY;
  }

  @Override
  public @NotNull JComponent getCentralStatusBarComponent() {
    return getNavBarPanel();
  }

  @Override
  public @NotNull String ID() {
    return getKey();
  }

  @Override
  public void install(@NotNull StatusBar statusBar) { }

  @Override
  public void dispose() { }

  private static boolean isShowToolPanel(@NotNull UISettings uiSettings) {
    // Evanescent me: fix run panel show condition in ExpUI if necessary.
    if (!ExperimentalUI.isNewUI() && uiSettings.getShowNavigationBar() &&
        !uiSettings.getShowMainToolbar() && !uiSettings.getPresentationMode()) {
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

  public static class NavBarWrapperPanel extends JPanel {
    public NavBarWrapperPanel(LayoutManager layout) {
      super(layout);
    }

    @Override
    protected Graphics getComponentGraphics(Graphics graphics) {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
    }
  }
}
