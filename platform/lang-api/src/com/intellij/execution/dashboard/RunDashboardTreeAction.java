/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author konstantin.aleev
 */
public abstract class RunDashboardTreeAction<T, C extends TreeContent> extends AnAction {
  protected RunDashboardTreeAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    List<T> targetNodes = getTargetNodes(e);

    boolean visible = isVisibleForAnySelection(e) || (!targetNodes.isEmpty() && targetNodes.stream().allMatch(this::isVisible4));
    boolean enabled = visible && (!targetNodes.isEmpty() && targetNodes.stream().allMatch(this::isEnabled4));

    presentation.setVisible(visible);
    presentation.setEnabled(enabled);
    updatePresentation(presentation, ContainerUtil.getFirstItem(targetNodes));
  }

  /**
   * Invokes {@link #collectNodes(AbstractTreeBuilder) collectNodes()} to collect nodes.
   * If each collected node could be casted to tree action node class,
   * returns a list of collected nodes casted to tree action node class, otherwise returns empty list.
   *
   * @param e Action event.
   * @return List of target nodes for this action.
   */
  @NotNull
  protected List<T> getTargetNodes(AnActionEvent e) {
    C content = getTreeContent(e);
    if (content == null) {
      return Collections.emptyList();
    }
    Set<?> selectedElements = collectNodes(content.getBuilder());
    int selectionCount = selectedElements.size();
    if (selectionCount == 0 || selectionCount > 1 && !isMultiSelectionAllowed()) {
      return Collections.emptyList();
    }
    Class<T> targetNodeClass = getTargetNodeClass();
    List<T> result = new ArrayList<>();
    for (Object selectedElement : selectedElements) {
      if (!targetNodeClass.isInstance(selectedElement)) {
        return Collections.emptyList();
      }
      result.add(targetNodeClass.cast(selectedElement));
    }
    return result;
  }

  /**
   * This implementation returns a set of selected nodes.
   * Subclasses may override this method to return modified nodes set.
   *
   * @param treeBuilder Tree builder.
   * @return Set of tree nodes for which action should be performed.
   */
  @NotNull
  protected Set<?> collectNodes(@NotNull AbstractTreeBuilder treeBuilder) {
    return treeBuilder.getSelectedElements();
  }

  protected abstract C getTreeContent(AnActionEvent e);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    C content = getTreeContent(e);
    if (content == null) return;

    List<T> verifiedTargetNodes = getTargetNodes(e).stream().filter(node -> isVisible4(node) && isEnabled4(node))
      .collect(Collectors.toList());
    doActionPerformed(content, e, verifiedTargetNodes);
  }

  protected boolean isVisibleForAnySelection(@NotNull AnActionEvent e) {
    return false;
  }

  protected boolean isMultiSelectionAllowed() {
    return false;
  }

  protected boolean isVisible4(T node) {
    return true;
  }

  protected boolean isEnabled4(T node) {
    return true;
  }

  protected void updatePresentation(@NotNull Presentation presentation, @Nullable T node) {
  }

  protected void doActionPerformed(@NotNull C content, AnActionEvent e, List<T> nodes) {
    nodes.forEach(node -> doActionPerformed(content, e, node));
  }

  protected void doActionPerformed(@NotNull C content, AnActionEvent e, T node) {
    doActionPerformed(node);
  }

  protected void doActionPerformed(T node) {
    throw new UnsupportedOperationException();
  }

  protected abstract Class<T> getTargetNodeClass();
}
