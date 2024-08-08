// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.services.ServiceViewActionUtils;
import com.intellij.execution.services.ServiceViewUIUtils;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.platform.navbar.frontend.ui.NavBarBorder;
import com.intellij.ui.*;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.Set;

final class ServiceViewTreeUi implements ServiceViewUi {
  private final JPanel myMainPanel;
  private final SimpleToolWindowPanel myContentPanel = new SimpleToolWindowPanel(false);
  private final Splitter mySplitter;
  private final JPanel myMasterPanel;
  private final JPanel myDetailsPanel;
  private final JPanel myContentComponentPanel;
  private final JPanel myNavBarPanel;
  private final JBPanelWithEmptyText myMessagePanel = new JBPanelWithEmptyText().withEmptyText(
    ExecutionBundle.message("service.view.empty.selection.text"));
  private final Set<JComponent> myDetailsComponents = ContainerUtil.createWeakSet();
  private ActionToolbar myServiceActionToolbar;
  private JComponent myServiceActionToolbarWrapper;
  private ActionToolbar myMasterActionToolbar;
  private NavBarWrapper myNavBarWrapper;

  ServiceViewTreeUi(@NotNull ServiceViewState state) {
    myMainPanel = new SimpleToolWindowPanel(false);

    myNavBarPanel = new JPanel(new BorderLayout());
    myNavBarPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    myMainPanel.add(myNavBarPanel, BorderLayout.NORTH);

    mySplitter = new OnePixelSplitter(false, state.contentProportion);
    myMainPanel.add(myContentPanel, BorderLayout.CENTER);
    myContentPanel.setContent(mySplitter);

    myMasterPanel = new JPanel(new BorderLayout());
    mySplitter.setFirstComponent(UiDataProvider.wrapComponent(myMasterPanel, sink -> {
      sink.set(ServiceViewActionUtils.IS_FROM_TREE_KEY, true);
    }));

    myDetailsPanel = new JPanel(new BorderLayout());
    myContentComponentPanel = new JPanel(new BorderLayout());
    myMessagePanel.setFocusable(true);
    myContentComponentPanel.add(myMessagePanel, BorderLayout.CENTER);
    myDetailsPanel.add(myContentComponentPanel, BorderLayout.CENTER);
    mySplitter.setSecondComponent(myDetailsPanel);

    if (state.showServicesTree) {
      myNavBarPanel.setVisible(false);
    }
    else {
      myMasterPanel.setVisible(false);
    }

    ComponentUtil
      .putClientProperty(myMainPanel, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, (Iterable<? extends Component>)(Iterable<JComponent>)() ->
        JBIterable.from(myDetailsComponents).append(myMessagePanel).filter(component -> myContentComponentPanel != component.getParent()).iterator());
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public void saveState(@NotNull ServiceViewState state) {
    state.contentProportion = mySplitter.getProportion();
  }

  @Override
  public void setServiceToolbar(@NotNull ServiceViewActionProvider actionProvider) {
    boolean inDetails = ServiceViewUIUtils.isNewServicesUIEnabled();
    myServiceActionToolbar = actionProvider.createServiceToolbar(myMainPanel, inDetails);
    if (inDetails) {
      JComponent wrapper = ServiceViewUIUtils.wrapServicesAligned(myServiceActionToolbar);
      myServiceActionToolbarWrapper = actionProvider.wrapServiceToolbar(wrapper, inDetails);
      myDetailsPanel.add(myServiceActionToolbarWrapper, BorderLayout.NORTH);
    }
    else {
      myContentPanel.setToolbar(actionProvider.wrapServiceToolbar(myServiceActionToolbar.getComponent(), inDetails));
    }
  }

  @Override
  public void setMasterComponent(@NotNull JComponent component, @NotNull ServiceViewActionProvider actionProvider) {
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(component, true);
    ScrollableContentBorder.setup(scrollPane, Side.TOP);
    myMasterPanel.add(scrollPane, BorderLayout.CENTER);

    myMasterActionToolbar = actionProvider.createMasterComponentToolbar(component);
    myMasterPanel.add(ServiceViewUIUtils.wrapServicesAligned(myMasterActionToolbar), BorderLayout.NORTH);
    myMasterPanel.updateUI();

    actionProvider.installPopupHandler(component);
  }

  @Override
  public void setNavBar(@NotNull JComponent component) {
    myNavBarWrapper = new NavBarWrapper(component);
    myNavBarWrapper.setVisible(myNavBarPanel.isVisible());
    myNavBarPanel.add(myNavBarWrapper, BorderLayout.CENTER);
  }

  @Override
  public @Nullable JComponent updateNavBar(boolean isSideComponent) {
    if (myNavBarWrapper != null) {
      if (isSideComponent) {
        if (myNavBarWrapper.getParent() == myNavBarPanel) {
          myNavBarPanel.remove(myNavBarWrapper);
        }
      }
      else if (myNavBarWrapper.getParent() != myNavBarPanel) {
        myNavBarPanel.add(myNavBarWrapper, BorderLayout.CENTER);
      }
      myNavBarWrapper.updateScrollPaneBorder(isSideComponent);
      setNavBarPanelVisible();
    }
    return myNavBarWrapper;
  }

  @Override
  public void setMasterComponentVisible(boolean visible) {
    myMasterPanel.setVisible(visible);
    if (myNavBarWrapper != null) {
      myNavBarWrapper.setVisible(!visible);
    }
    setNavBarPanelVisible();
  }

  private void setNavBarPanelVisible() {
    if (myNavBarWrapper != null) {
      myNavBarPanel.setVisible(myNavBarWrapper.isVisible() && myNavBarWrapper.getParent() == myNavBarPanel);
    }
  }

  @Override
  public void setDetailsComponent(@Nullable JComponent component) {
    if (component == null) {
      component = myMessagePanel;
    }
    if (component.getParent() != myContentComponentPanel) {
      if (ServiceViewUIUtils.isNewServicesUIEnabled()) {
        boolean visible = ServiceViewActionProvider.isActionToolBarRequired(component);
        myServiceActionToolbarWrapper.setVisible(visible);
        if (visible) {
          myContentComponentPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
        }
        else {
          myContentComponentPanel.setBorder(null);
        }
      }

      myDetailsComponents.add(component);
      myContentComponentPanel.removeAll();
      myContentComponentPanel.add(component, BorderLayout.CENTER);
      myContentComponentPanel.revalidate();
      myContentComponentPanel.repaint();
    }
    ActionToolbar serviceActionToolbar = myServiceActionToolbar;
    if (serviceActionToolbar != null) {
      ((ActionToolbarImpl)serviceActionToolbar).reset();
      serviceActionToolbar.updateActionsImmediately();
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      ActionToolbar masterActionToolbar = myMasterActionToolbar;
      if (masterActionToolbar != null) {
        masterActionToolbar.updateActionsImmediately();
      }
    });
  }

  @Override
  public void setDetailsComponentVisible(boolean visible) {
    myDetailsPanel.setVisible(visible);
  }

  @Nullable
  @Override
  public JComponent getDetailsComponent() {
    int count = myContentComponentPanel.getComponentCount();
    if (count == 0) return null;

    Component component = myContentComponentPanel.getComponent(0);
    return component == myMessagePanel ? null : (JComponent)component;
  }

  @Override
  public void setSplitOrientation(boolean verticalSplit) {
    mySplitter.setOrientation(verticalSplit);
  }

  private static class NavBarWrapper extends JPanel {
    private final JScrollPane myScrollPane;
    private final Border mySideBorder;
    private final Border myNavBarBorder;

    NavBarWrapper(JComponent component) {
      super(new BorderLayout());
      myScrollPane = ScrollPaneFactory.createScrollPane(component);
      myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
      myScrollPane.setHorizontalScrollBar(null);
      mySideBorder = JBUI.Borders.empty();
      myNavBarBorder = new NavBarBorder();
      myScrollPane.setBorder(mySideBorder);
      add(myScrollPane, BorderLayout.CENTER);
    }

    @Override
    public void doLayout() {
      // align vertically
      Rectangle r = getBounds();
      Insets insets = getInsets();
      int x = insets.left;
      Dimension preferredSize = myScrollPane.getPreferredSize();
      myScrollPane.setBounds(x, (r.height - preferredSize.height) / 2, r.width - insets.left - insets.right, preferredSize.height);
    }

    void updateScrollPaneBorder(boolean isSideComponent) {
      Border border = isSideComponent ? mySideBorder : myNavBarBorder;
      if (myScrollPane.getBorder() != border) {
        myScrollPane.setBorder(border);
      }
    }
  }
}
