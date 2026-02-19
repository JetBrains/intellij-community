// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi.frontend;

import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.execution.services.ServiceViewUIUtils;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Insets;

@ApiStatus.Internal
public final class RunDashboardUiUtils {
  private RunDashboardUiUtils() {
  }

  public static final @NonNls String RUN_DASHBOARD_CONTENT_TOOLBAR = "RunDashboardContentToolbar";

  public static void updateContentToolbar(Content content, boolean visible) {
    RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
    RunnerLayoutUiImpl ui = getRunnerLayoutUi(descriptor);
    if (ui != null) {
      if (!ServiceViewUIUtils.isNewServicesUIEnabled()) {
        ui.setLeftToolbarVisible(visible);
      }
      ui.setContentToolbarBefore(visible);
    }
    else {
      ActionToolbar toolbar = findActionToolbar(descriptor);
      if (toolbar != null) {
        toolbar.getComponent().setVisible(visible);
      }
    }
  }

  public static void setupToolbar(@NotNull JPanel mainPanel, @NotNull JComponent component, @NotNull Project project) {
    if (ServiceViewUIUtils.isNewServicesUIEnabled()) {
      if (ActionManager.getInstance().getAction(RUN_DASHBOARD_CONTENT_TOOLBAR) instanceof ActionGroup group) {
        group.registerCustomShortcutSet(component, project);
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.SERVICES_TOOLBAR, group, true);
        toolbar.setTargetComponent(component);
        mainPanel.add(ServiceViewUIUtils.wrapServicesAligned(toolbar), BorderLayout.NORTH);
        int left = 0;
        int right = 0;
        Border border = toolbar.getComponent().getBorder();
        if (border != null) {
          Insets insets = border.getBorderInsets(toolbar.getComponent());
          left = insets.left;
          right = insets.right;
        }
        toolbar.getComponent().setBorder(JBUI.Borders.empty(1, left, 0, right));
        component.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
      }
    }
    ClientProperty.put(mainPanel, ServiceViewDescriptor.ACTION_HOLDER_KEY, Boolean.TRUE);
  }

  public static @Nullable RunnerLayoutUiImpl getRunnerLayoutUi(@Nullable RunContentDescriptor descriptor) {
    if (descriptor == null) return null;

    RunnerLayoutUi layoutUi = descriptor.getRunnerLayoutUi();
    return layoutUi instanceof RunnerLayoutUiImpl ? (RunnerLayoutUiImpl)layoutUi : null;
  }

  public static @Nullable ActionToolbar findActionToolbar(@Nullable RunContentDescriptor descriptor) {
    if (descriptor == null) return null;

    JComponent descriptorComponent = descriptor.getComponent();
    if (descriptorComponent == null) return null;
    for (Component component : descriptorComponent.getComponents()) {
      if (component instanceof ActionToolbar) {
        return ((ActionToolbar)component);
      }
    }
    return null;
  }
}
