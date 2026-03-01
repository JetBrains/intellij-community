// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi.frontend.tree;

import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.dashboard.LegacyRunDashboardServiceSubstitutor;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationStatus;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.ui.icons.IconId;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardServiceDto;
import com.intellij.platform.execution.dashboard.splitApi.ServiceCustomizationDto;
import com.intellij.platform.execution.dashboard.splitApi.TextSegmentWithAttributesDto;
import com.intellij.platform.execution.dashboard.splitApi.frontend.FrontendDashboardLuxComponent;
import com.intellij.platform.execution.dashboard.splitApi.frontend.FrontendRunDashboardLuxHolder;
import com.intellij.platform.execution.dashboard.splitApi.frontend.FrontendRunDashboardManager;
import com.intellij.platform.execution.dashboard.splitApi.frontend.FrontendRunDashboardService;
import com.intellij.platform.ide.productMode.IdeProductMode;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.content.Content;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.Icon;
import java.util.Collection;
import java.util.Collections;

import static com.intellij.ide.ui.icons.IconIdKt.icon;

public class FrontendRunConfigurationNode extends AbstractTreeNode<FrontendRunDashboardService>
  implements RunDashboardRunConfigurationNode {
  public FrontendRunConfigurationNode(@NotNull Project project, @NotNull FrontendRunDashboardService value) {
    super(project, value);
  }

  @Override
  public @Unmodifiable @NotNull Collection<? extends AbstractTreeNode<?>> getChildren() {
    return Collections.emptyList();
  }

  @SuppressWarnings("DataFlowIssue")
  public @NotNull RunDashboardServiceDto getService() {
    return getValue().getRunDashboardServiceDto();
  }

  @Override
  public @Nullable Content getContent() {
    RunContentDescriptor descriptor = getDescriptor();
    return descriptor == null ? null : descriptor.getAttachedContent();
  }

  @Override
  @SuppressWarnings("DataFlowIssue")
  public @Nullable RunContentDescriptor getDescriptor() {
    return FrontendRunDashboardManager.getInstance(getProject()).getServiceRunContentDescriptor(getValue());
  }

  @Override
  public RunnerAndConfigurationSettings getConfigurationSettings() {
    Logger.getInstance(FrontendRunConfigurationNode.class)
      .debug("Deprecated method `getConfigurationSettings` called for " + getService().getName());
    if (IdeProductMode.isMonolith()) {
      var substitutor = ContainerUtil.getFirstItem(LegacyRunDashboardServiceSubstitutor.EP_NAME.getExtensionList());
      if (substitutor == null) return null;

      var backendService = substitutor.substituteWithBackendService(this, getProject());
      if (backendService != this) {
        return backendService.getConfigurationSettings();
      }
    }
    return null;
  }

  public @Nullable RunDashboardRunConfigurationStatus getStatus() {
    var statusId = FrontendRunDashboardManager.getInstance(getProject()).getStatusById(getService().getUuid());
    return RunDashboardRunConfigurationStatus.getStatusById(statusId);
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    var frontendService = getValue().getRunDashboardServiceDto();

    SimpleTextAttributes nameAttributes;
    if (frontendService.isStored()) {
      var hasRegularOrLuxedContent = getContent() != null ||
                                     FrontendRunDashboardLuxHolder.getInstance(getProject())
                                       .getComponentOrNull(frontendService.getUuid()) != null;
      nameAttributes = hasRegularOrLuxedContent ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
    else {
      nameAttributes = SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES;
    }
    presentation.addText(frontendService.getName(), nameAttributes);
    Icon icon = getNodeIcon();
    presentation.setIcon(frontendService.isStored() ? icon : IconLoader.createLazy(() -> IconLoader.getDisabledIcon(icon)));

    ServiceCustomizationDto customization =
      FrontendRunDashboardManager.getInstance(getProject()).getCustomizationById(getValue().getRunDashboardServiceDto().getUuid());
    if (customization == null) return;
    applyCustomizationToPresentation(presentation, customization);
  }

  private static void applyCustomizationToPresentation(PresentationData mutableTargetPresentation, ServiceCustomizationDto customization) {
    if (customization.getShouldClearTextAttributes()) {
      mutableTargetPresentation.clearText();
    }

    for (TextSegmentWithAttributesDto textSegment : customization.getText()) {
      mutableTargetPresentation.addText(textSegment.getValue(), textSegment.getAttributes().getSimpleTextAttributes());
    }

    Icon icon = customization.getIconId() == null ? null : icon(customization.getIconId());
    if (icon != null) {
      mutableTargetPresentation.setIcon(icon);
    }
  }

  private Icon getNodeIcon() {
    Icon icon = null;
    var status = getStatus();
    if (RunDashboardRunConfigurationStatus.STARTED.equals(status)) {
      icon = getExecutorIcon();
    }
    else if (RunDashboardRunConfigurationStatus.FAILED.equals(status)) {
      icon = status.getIcon();
    }
    if (icon == null) {
      IconId iconId = getValue().getRunDashboardServiceDto().getIconId();
      icon = iconId == null ? null : icon(iconId);
    }
    return icon;
  }

  private @Nullable Icon getExecutorIcon() {
    FrontendDashboardLuxComponent luxedComponent =
      FrontendRunDashboardLuxHolder.getInstance(myProject).getComponentOrNull(getValue().getRunDashboardServiceDto().getUuid());
    if (luxedComponent != null && ToolWindowId.RUN.equals(luxedComponent.getExecutorId())) {
      return DefaultRunExecutor.getRunExecutorInstance().getIcon();
    }

    Content content = getContent();
    if (content != null) {
      if (!RunContentManagerImpl.isTerminated(content)) {
        Executor executor = RunContentManagerImpl.getExecutorByContent(content);
        if (executor != null) {
          return executor.getIcon();
        }
      }
    }
    return null;
  }
}
