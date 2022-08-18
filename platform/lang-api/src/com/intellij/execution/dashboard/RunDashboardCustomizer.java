// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * Register this extension to customize Run Dashboard.
 */
public abstract class RunDashboardCustomizer {
  public static final Key<Map<Object, Object>> NODE_LINKS = new Key<>("RunDashboardNodeLink");

  public abstract boolean isApplicable(@NotNull RunnerAndConfigurationSettings settings, @Nullable RunContentDescriptor descriptor);

  public boolean updatePresentation(@NotNull PresentationData presentation, @NotNull RunDashboardRunConfigurationNode node) {
    return false;
  }

  /**
   * Returns node's status. Subclasses may override this method to provide custom statuses.
   *
   * @param node dashboard node
   * @return node's status. Returned status is used for grouping nodes by status.
   */
  @Nullable
  public RunDashboardRunConfigurationStatus getStatus(@NotNull RunDashboardRunConfigurationNode node) {
    return null;
  }

  @Nullable
  public PsiElement getPsiElement(@NotNull RunDashboardRunConfigurationNode node) {
    return null;
  }

  @Nullable
  public Collection<? extends AbstractTreeNode<?>> getChildren(@NotNull RunDashboardRunConfigurationNode node) {
    return null;
  }

  public boolean canDrop(@NotNull RunDashboardRunConfigurationNode node, @NotNull DnDEvent event) {
    return false;
  }

  public void drop(@NotNull RunDashboardRunConfigurationNode node, @NotNull DnDEvent event) {
  }
}
