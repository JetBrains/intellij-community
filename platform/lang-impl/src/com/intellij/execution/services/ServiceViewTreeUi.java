// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.DataManager;
import com.intellij.ide.navigationToolbar.NavBarBorder;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.*;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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

  ServiceViewTreeUi(@NotNull ServiceViewState state) {
    myMainPanel = new SimpleToolWindowPanel(false);

    myNavBarPanel = new JPanel(new BorderLayout());
    myNavBarPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    myMainPanel.add(myNavBarPanel, BorderLayout.NORTH);

    mySplitter = new OnePixelSplitter(false, state.contentProportion);
    myMainPanel.add(myContentPanel, BorderLayout.CENTER);
    myContentPanel.setContent(mySplitter);

    myMasterPanel = new JPanel(new BorderLayout());
    DataManager.registerDataProvider(myMasterPanel, dataId -> {
      if (ServiceViewActionUtils.IS_FROM_TREE_KEY.is(dataId)) {
        return true;
      }
      return null;
    });

    mySplitter.setFirstComponent(myMasterPanel);

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
    myMasterPanel.add(ScrollPaneFactory.createScrollPane(component, SideBorder.TOP), BorderLayout.CENTER);

    myMasterActionToolbar = actionProvider.createMasterComponentToolbar(component);
    myMasterPanel.add(ServiceViewUIUtils.wrapServicesAligned(myMasterActionToolbar), BorderLayout.NORTH);
    myMasterPanel.updateUI();

    actionProvider.installPopupHandler(component);
  }

  @Override
  public void setNavBar(@NotNull JComponent component) {
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(component);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    scrollPane.setHorizontalScrollBar(null);
    scrollPane.setBorder(new NavBarBorder());
    JPanel navBarPanelWrapper = new JPanel(new BorderLayout()) {
      @Override
      public void doLayout() {
        // align vertically
        Rectangle r = getBounds();
        Insets insets = getInsets();
        int x = insets.left;
        Dimension preferredSize = scrollPane.getPreferredSize();
        scrollPane.setBounds(x, (r.height - preferredSize.height) / 2, r.width - insets.left - insets.right, preferredSize.height);
      }
    };
    navBarPanelWrapper.add(scrollPane, BorderLayout.CENTER);
    myNavBarPanel.add(navBarPanelWrapper, BorderLayout.CENTER);
  }

  @Override
  public void setMasterComponentVisible(boolean visible) {
    myMasterPanel.setVisible(visible);
    myNavBarPanel.setVisible(!visible);
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

  @Nullable
  @Override
  public JComponent getDetailsComponent() {
    int count = myContentComponentPanel.getComponentCount();
    if (count == 0) return null;

    Component component = myContentComponentPanel.getComponent(0);
    return component == myMessagePanel ? null : (JComponent)component;
  }
}
