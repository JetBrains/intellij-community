/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.CompositePackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class MovePackagingElementAction extends DumbAwareAction {
  private final LayoutTreeComponent myLayoutTreeComponent;
  private final int myDirection;

  public MovePackagingElementAction(LayoutTreeComponent layoutTreeComponent, String text, String description, Icon icon, int direction) {
    super(text, description, icon);
    myLayoutTreeComponent = layoutTreeComponent;
    myDirection = direction;
  }

  @Override
  public void update(AnActionEvent e) {
    final boolean b = isEnabled();
    e.getPresentation().setEnabled(b);
    e.getPresentation().setText(getTemplatePresentation().getText() + " (disabled if elements are sorted)");
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
  public void actionPerformed(AnActionEvent e) {
    final PackagingElementNode<?> node = myLayoutTreeComponent.getSelection().getNodeIfSingle();
    if (node == null) return;
    final CompositePackagingElementNode parent = node.getParentNode();
    if (parent == null) return;

    final PackagingElement<?> element = node.getElementIfSingle();
    final CompositePackagingElement<?> parentElement = parent.getElementIfSingle();
    if (parentElement == null || element == null) return;


    if (!myLayoutTreeComponent.checkCanModifyChildren(parentElement, parent, Arrays.asList(node))) return;

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
