// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.services.ServiceViewUIUtils;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

final class ServiceViewSingleUi implements ServiceViewUi {
  private final SimpleToolWindowPanel myMainPanel = new SimpleToolWindowPanel(isHorizontal());
  private final JPanel myMessagePanel = new JBPanelWithEmptyText().withEmptyText(ExecutionBundle.message("service.view.empty.tab.text"));

  ServiceViewSingleUi() {
    ComponentUtil.putClientProperty(myMainPanel, UIUtil.NOT_IN_HIERARCHY_COMPONENTS,
                                    (Iterable<? extends Component>)(Iterable<JComponent>)() -> JBIterable.of((JComponent)myMessagePanel)
                                      .filter(component -> myMainPanel != component.getParent()).iterator());
    myMessagePanel.setFocusable(true);
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public void saveState(@NotNull ServiceViewState state) {
  }

  @Override
  public void setServiceToolbar(@NotNull ServiceViewActionProvider actionManager) {
    boolean horizontal = isHorizontal();
    ActionToolbar toolbar = actionManager.createServiceToolbar(myMainPanel, horizontal);
    myMainPanel.setToolbar(actionManager.wrapServiceToolbar(toolbar.getComponent(), horizontal));
  }

  @Override
  public void setMasterComponent(@NotNull JComponent component, @NotNull ServiceViewActionProvider actionManager) {
  }

  @Override
  public void setDetailsComponent(@Nullable JComponent component) {
    if (component == null) {
      component = myMessagePanel;
    }
    if (component.getParent() == myMainPanel) return;

    myMainPanel.setContent(component);
    if (ServiceViewUIUtils.isNewServicesUIEnabled()) {
      myMainPanel.getToolbar().setVisible(ServiceViewActionProvider.isActionToolBarRequired(component));
    }
  }

  @Override
  public void setDetailsComponentVisible(boolean visible) {
  }

  @Override
  public void setNavBar(@NotNull JComponent component) {
  }

  @Override
  public @Nullable JComponent updateNavBar(boolean isSideComponent) {
    return null;
  }

  @Override
  public void setMasterComponentVisible(boolean visible) {
  }

  @Override
  public @Nullable JComponent getDetailsComponent() {
    JComponent content = myMainPanel.getContent();
    return content == myMessagePanel ? null : content;
  }

  @Override
  public void setSplitOrientation(boolean verticalSplit) {
  }

  private static boolean isHorizontal() {
    return ServiceViewUIUtils.isNewServicesUIEnabled();
  }
}
