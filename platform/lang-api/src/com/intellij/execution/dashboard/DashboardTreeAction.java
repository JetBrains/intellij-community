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
import java.util.List;
import java.util.Set;

/**
 * @author konstantin.aleev
 */
public abstract class DashboardTreeAction<T, C extends TreeContent> extends AnAction {
  protected DashboardTreeAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    List<T> targetNodes = getTargetNodes(e);

    boolean visible;
    boolean enabled;

    if (targetNodes == null) {
      visible = false;
      enabled = false;
    }
    else {
      visible = true;
      enabled = true;
      for (T targetNode : targetNodes) {
        visible &= isVisible4(targetNode);
        enabled &= visible && isEnabled4(targetNode);
      }
    }

    presentation.setVisible(visible);
    presentation.setEnabled(enabled);
    updatePresentation(presentation, ContainerUtil.getFirstItem(targetNodes));
  }

  /**
   * Invokes {@link #collectNodes(AbstractTreeBuilder) collectNodes()} to collect nodes.
   * If each collected node could be casted to tree action node class,
   * returns a list of collected nodes casted to tree action node class, otherwise returns {@code null}.
   *
   * @param e Action event.
   * @return List of target nodes for this action.
   */
  @Nullable
  protected List<T> getTargetNodes(AnActionEvent e) {
    C content = getTreeContent(e);
    if (content == null) {
      return null;
    }
    Set<?> selectedElements = collectNodes(content.getBuilder());
    int selectionCount = selectedElements.size();
    if (selectionCount == 0 || selectionCount > 1 && !isMultiSelectionAllowed()) {
      return null;
    }
    Class<T> targetNodeClass = getTargetNodeClass();
    List<T> result = new ArrayList<>();
    for (Object selectedElement : selectedElements) {
      if (!targetNodeClass.isInstance(selectedElement)) {
        return null;
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
    List<T> targetNodes = getTargetNodes(e);
    if (targetNodes == null) {
      return;
    }

    List<T> verifiedTargetNodes = ContainerUtil.filter(targetNodes, targetNode -> isVisible4(targetNode) && isEnabled4(targetNode));
    doActionPerformed(getTreeContent(e), e, verifiedTargetNodes);
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
    for (T node : nodes) {
      doActionPerformed(content, e, node);
    }
  }

  protected void doActionPerformed(@NotNull C content, AnActionEvent e, T node) {
    doActionPerformed(node);
  }

  protected void doActionPerformed(T node) {
    throw new UnsupportedOperationException();
  }

  protected abstract Class<T> getTargetNodeClass();
}
