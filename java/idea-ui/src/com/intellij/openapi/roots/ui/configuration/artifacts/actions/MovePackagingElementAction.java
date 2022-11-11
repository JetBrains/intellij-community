// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.CompositePackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.openapi.util.NlsActions;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MovePackagingElementAction extends DumbAwareAction {
  private final LayoutTreeComponent myLayoutTreeComponent;
  private final int myDirection;

  public MovePackagingElementAction(LayoutTreeComponent layoutTreeComponent,
                                    @Nullable @NlsActions.ActionText String text,
                                    @Nullable @NlsActions.ActionDescription String description,
                                    @Nullable Icon icon,
                                    int direction) {
    super(text, description, icon);
    myLayoutTreeComponent = layoutTreeComponent;
    myDirection = direction;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final boolean b = isEnabled();
    e.getPresentation().setEnabled(b);
    e.getPresentation().setText(JavaUiBundle.message("action.text.0.disabled.if.elements.are.sorted", getTemplatePresentation().getText()));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  private boolean isEnabled() {
    if (myLayoutTreeComponent.isSortElements()) {
      return false;
    }
    final PackagingElementNode<?> node = myLayoutTreeComponent.getSelection().getNodeIfSingle();
    if (node == null) {
      return false;
    }
    final CompositePackagingElementNode parent = node.getParentNode();
    if (parent == null) return false;

    final PackagingElement<?> element = node.getElementIfSingle();
    final CompositePackagingElement<?> parentElement = parent.getElementIfSingle();
    if (parentElement == null || element == null) return false;
    final List<PackagingElement<?>> children = parentElement.getChildren();
    final int index = children.indexOf(element);
    return index != -1 && 0 <= index + myDirection && index + myDirection < children.size();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final PackagingElementNode<?> node = myLayoutTreeComponent.getSelection().getNodeIfSingle();
    if (node == null) return;
    final CompositePackagingElementNode parent = node.getParentNode();
    if (parent == null) return;

    final PackagingElement<?> element = node.getElementIfSingle();
    final CompositePackagingElement<?> parentElement = parent.getElementIfSingle();
    if (parentElement == null || element == null) return;


    if (!myLayoutTreeComponent.checkCanModifyChildren(parentElement, parent, Collections.singletonList(node))) return;

    final List<PackagingElement<?>> toSelect = new ArrayList<>();
    myLayoutTreeComponent.editLayout(() -> {
      final int index = parentElement.getChildren().indexOf(element);
      final PackagingElement<?> moved = parentElement.moveChild(index, myDirection);
      if (moved != null) {
        toSelect.add(moved);
      }
    });
    if (!toSelect.isEmpty()) {
      myLayoutTreeComponent.updateAndSelect(parent, toSelect);
    }
  }
}
