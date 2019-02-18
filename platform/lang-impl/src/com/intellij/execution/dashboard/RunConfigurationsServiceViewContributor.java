// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.actions.StopAction;
import com.intellij.execution.dashboard.tree.RunConfigurationNode;
import com.intellij.execution.dashboard.tree.RunDashboardTreeCellRenderer;
import com.intellij.execution.runners.FakeRerunAction;
import com.intellij.execution.services.ServiceViewContributor;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl;
import com.intellij.ide.DataManager;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.execution.dashboard.RunDashboardContent.RUN_DASHBOARD_CONTENT_TOOLBAR;
import static com.intellij.execution.dashboard.RunDashboardContent.RUN_DASHBOARD_TREE_TOOLBAR;
import static com.intellij.execution.dashboard.RunDashboardManagerImpl.getRunnerLayoutUi;
import static com.intellij.openapi.actionSystem.ActionPlaces.RUN_DASHBOARD_POPUP;

public class RunConfigurationsServiceViewContributor
  implements ServiceViewContributor<RunConfigurationNode, RunDashboardGroup, RunDashboardRunConfigurationStatus> {
  @NotNull
  @Override
  public List<RunConfigurationNode> getNodes(@NotNull Project project) {
    RunDashboardManager runDashboardManager = RunDashboardManager.getInstance(project);
    return ContainerUtil.map(runDashboardManager.getRunConfigurations(),
                             value -> new RunConfigurationNode(project, value, runDashboardManager.getContributor(value.first.getType())));
  }

  @NotNull
  @Override
  public ViewDescriptor getNodeDescriptor(@NotNull RunConfigurationNode node) {
    return new ViewDescriptor() {
      private boolean selected;

      @Override
      public JComponent getContentComponent() {
        Content content = node.getContent();
        return content == null ? createEmptyContent() : content.getManager().getComponent();
      }

      @Override
      public ActionGroup getToolbarActions() {
        return RunConfigurationsServiceViewContributor.getToolbarActions(node.getDescriptor());
      }

      @Override
      public ActionGroup getPopupActions() {
        return RunConfigurationsServiceViewContributor.getPopupActions();
      }

      @Override
      public ItemPresentation getPresentation() {
        return node.getPresentation();
      }

      @Override
      public DataProvider getDataProvider() {
        Content content = node.getContent();
        if (content == null) return null;

        DataContext context = DataManager.getInstance().getDataContext(content.getComponent());
        return context::getData;
      }

      @Override
      public void onNodeSelected() {
        selected = true;
        Content content = node.getContent();
        ContentManager contentManager = content == null ? null : content.getManager();
        if (contentManager == null || content == contentManager.getSelectedContent()) return;

        // Invoke content selection change later after currently selected content lost a focus.
        SwingUtilities.invokeLater(() -> {
          // Selected node may changed, we do not need to select content if it doesn't correspond currently selected node.
          if (contentManager.isDisposed() || contentManager.getIndexOfContent(content) == -1 || !selected) return;

          contentManager.setSelectedContent(content);
        });
      }

      @Override
      public void onNodeUnselected() {
        selected = false;
        Content content = node.getContent();
        ContentManager contentManager = content == null ? null : content.getManager();
        if (contentManager == null || content != contentManager.getSelectedContent()) return;

        // Invoke content selection change later after currently selected content correctly restores its state,
        // since RunnerContentUi performs restoring later after addNotify call chain.
        SwingUtilities.invokeLater(() -> {
          // Selected node may changed, we do not need to remove content from selection if it corresponds currently selected node.
          if (contentManager.isDisposed() || !contentManager.isSelected(content) || selected) return;

          contentManager.removeFromSelection(content);
        });
      }
    };
  }

  @Nullable
  @Override
  public ViewDescriptorRenderer getViewDescriptorRenderer() {
    return new ViewDescriptorRenderer() {
      final RunDashboardTreeCellRenderer renderer = new RunDashboardTreeCellRenderer();
      @NotNull
      @Override
      public Component getRendererComponent(JComponent parent,
                                            Object value,
                                            ViewDescriptor viewDescriptor,
                                            boolean selected,
                                            boolean hasFocus) {
        return renderer.getTreeCellRendererComponent((JTree)parent, value, selected, true, true, 0, hasFocus);
      }
    };
  }

  @NotNull
  @Override
  public List<RunDashboardGroup> getGroups(@NotNull RunConfigurationNode node) {
    List<RunDashboardGroup> result = new ArrayList<>();
    for (RunDashboardGroupingRule rule : RunDashboardGroupingRule.EP_NAME.getExtensionList()) {
      ContainerUtil.addIfNotNull(result, rule.getGroup(node));
    }
    return result;
  }

  @NotNull
  @Override
  public ViewDescriptor getGroupDescriptor(@NotNull RunDashboardGroup group) {
    PresentationData presentationData = new PresentationData();
    presentationData.setPresentableText(group.getName());
    presentationData.setIcon(group.getIcon());
    return new ViewDescriptor() {
      @Override
      public JComponent getContentComponent() {
        return null;
      }

      @Override
      public ActionGroup getToolbarActions() {
        return RunConfigurationsServiceViewContributor.getToolbarActions(null);
      }

      @Override
      public ActionGroup getPopupActions() {
        return RunConfigurationsServiceViewContributor.getPopupActions();
      }

      @Override
      public ItemPresentation getPresentation() {
        return presentationData;
      }

      @Override
      public DataProvider getDataProvider() {
        return null;
      }
    };
  }

  @NotNull
  @Override
  public RunDashboardRunConfigurationStatus getState(@NotNull RunConfigurationNode node) {
    return node.getStatus();
  }

  @NotNull
  @Override
  public ViewDescriptor getStateDescriptor(@NotNull RunDashboardRunConfigurationStatus status) {
    PresentationData presentationData = new PresentationData();
    presentationData.setPresentableText(status.getName());
    presentationData.setIcon(status.getIcon());
    return new ViewDescriptor() {
      @Override
      public JComponent getContentComponent() {
        return null;
      }

      @Override
      public ActionGroup getToolbarActions() {
        return null;
      }

      @Override
      public ItemPresentation getPresentation() {
        return presentationData;
      }

      @Override
      public DataProvider getDataProvider() {
        return null;
      }
    };
  }

  @NotNull
  private static JComponent createEmptyContent() {
    return new JBPanelWithEmptyText().withEmptyText(ExecutionBundle.message("run.dashboard.not.started.configuration.message"));
  }

  private static ActionGroup getToolbarActions(@Nullable RunContentDescriptor descriptor) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(ActionManager.getInstance().getAction(RUN_DASHBOARD_CONTENT_TOOLBAR));

    RunnerLayoutUiImpl ui = getRunnerLayoutUi(descriptor);
    if (ui == null) return actionGroup;

    List<AnAction> leftToolbarActions = ui.getActions();
    for (AnAction action : leftToolbarActions) {
      if (!(action instanceof StopAction) && !(action instanceof FakeRerunAction)) {
        actionGroup.add(action);
      }
    }
    return actionGroup;
  }

  private static ActionGroup getPopupActions() {
    DefaultActionGroup actions = new DefaultActionGroup();
    ActionManager actionManager = ActionManager.getInstance();
    actions.add(actionManager.getAction(RUN_DASHBOARD_CONTENT_TOOLBAR));
    actions.addSeparator();
    actions.add(actionManager.getAction(RUN_DASHBOARD_TREE_TOOLBAR));
    actions.add(actionManager.getAction(RUN_DASHBOARD_POPUP));
    return actions;
  }
}
