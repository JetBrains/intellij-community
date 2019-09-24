/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.dashboard;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * Register this extension to customize Run Dashboard.
 */
@ApiStatus.Experimental
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
  public Collection<? extends AbstractTreeNode> getChildren(@NotNull RunDashboardRunConfigurationNode node) {
    return null;
  }

  public boolean canDrop(@NotNull RunDashboardRunConfigurationNode node, @NotNull DnDEvent event) {
    return false;
  }

  public void drop(@NotNull RunDashboardRunConfigurationNode node, @NotNull DnDEvent event) {
  }
}
