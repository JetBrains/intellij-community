package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.CompositePackagingElementNode;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class MoveElementAction extends DumbAwareAction {
  private final LayoutTreeComponent myLayoutTreeComponent;
  private final int myDirection;

  public MoveElementAction(LayoutTreeComponent layoutTreeComponent, String text, String description, Icon icon, int direction) {
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


    if (myLayoutTreeComponent.checkCanRemove(Collections.singletonList(node))
        && myLayoutTreeComponent.checkCanAdd(null, parentElement, parent)) {
      myLayoutTreeComponent.ensureRootIsWritable();
      final int index = parentElement.getChildren().indexOf(element);
      final PackagingElement<?> moved = parentElement.moveChild(index, myDirection);
      if (moved != null) {
        myLayoutTreeComponent.updateAndSelect(parent, Collections.singletonList(moved));
      }
    }
  }
}
