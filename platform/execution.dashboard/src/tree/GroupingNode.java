// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.tree;

import com.intellij.execution.dashboard.RunDashboardGroup;
import com.intellij.execution.dashboard.RunDashboardNode;
import com.intellij.execution.dashboard.actions.RunDashboardGroupNode;
import com.intellij.execution.services.ServiceViewManager;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.platform.execution.dashboard.RunDashboardServiceViewContributor;
import com.intellij.platform.execution.serviceView.ServiceViewManagerImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author konstantin.aleev
 */
public final class GroupingNode extends AbstractTreeNode<Pair<Object, RunDashboardGroup>>
  implements RunDashboardNode,
             RunDashboardGroupNode {
  private final List<AbstractTreeNode<?>> myChildren = new ArrayList<>();

  public GroupingNode(Project project, Object parent, RunDashboardGroup group) {
    super(project, Pair.create(parent, group));
  }

  public RunDashboardGroup getGroup() {
    //noinspection ConstantConditions ???
    return getValue().getSecond();
  }

  @Override
  public @NotNull Collection<? extends AbstractTreeNode<?>> getChildren() {
    return myChildren;
  }

  public void addChildren(Collection<? extends AbstractTreeNode<?>> children) {
    myChildren.addAll(children);
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setPresentableText(getGroup().getName());
    presentation.setIcon(getGroup().getIcon());
  }

  @Override
  public @NotNull List<@NotNull Object> getChildren(@NotNull Project project, @NotNull AnActionEvent e) {
    return ((ServiceViewManagerImpl)ServiceViewManager.getInstance(project))
      .getChildrenSafe(e, List.of(this), RunDashboardServiceViewContributor.class);
  }
}
