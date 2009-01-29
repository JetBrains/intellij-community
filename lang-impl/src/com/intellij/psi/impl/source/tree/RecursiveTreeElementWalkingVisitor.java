package com.intellij.psi.impl.source.tree;

import com.intellij.psi.impl.source.parsing.ChameleonTransforming;

/*
  @Override public void visitLeaf(LeafElement leaf) {
    visitNode(leaf);
  }

  @Override public void visitComposite(CompositeElement composite) {
    ChameleonTransforming.transformChildren(composite);
    if(!visitNode(composite)) return;
    TreeElement child = composite.getFirstChildNode();
    while(child != null) {
      final TreeElement treeNext = child.getTreeNext();
      child.acceptTree(this);
      child = treeNext;
    }
  }

  protected abstract boolean visitNode(TreeElement element);
 */


/*
  private boolean startedWalking;
  private boolean isDown;
  @Override public void visitLeaf(LeafElement leaf) {
    visitNode(leaf);
  }

  @Override public void visitComposite(CompositeElement composite) {
    ChameleonTransforming.transformChildren(composite);
    isDown = visitNode(composite);
    if (!startedWalking) {
      startedWalking = true;
      walk(composite);
    }
  }

  private void walk(TreeElement root) {
    for (TreeElement element = next(root, root); element != null; element = next(element, root)) {
      ChameleonTransforming.transformChildren(element);
      element.acceptTree(this);
    }
  }

  private TreeElement next(TreeElement element, TreeElement root) {
    if (isDown) {
      TreeElement child = element.getFirstChildNode();
      if (child != null) return child;
    }
    else {
      isDown = true;
    }

    // up
    while (element != root) {
      TreeElement next = element.getTreeNext();
      if (next != null) return next;
      element = element.getTreeParent();
    }
    return null;
  }

  protected abstract boolean visitNode(TreeElement element);
*/

public abstract class RecursiveTreeElementWalkingVisitor extends TreeElementVisitor{
  private boolean startedWalking;
  private boolean isDown;
  @Override public void visitLeaf(LeafElement leaf) {
    visitNode(leaf);
  }

  @Override public void visitComposite(CompositeElement composite) {
    ChameleonTransforming.transformChildren(composite);
    isDown = visitNode(composite);
    if (!startedWalking) {
      startedWalking = true;
      walk(composite);
    }
  }

  private void walk(TreeElement root) {
    for (TreeElement element = next(root, root); element != null; element = next(element, root)) {
      ChameleonTransforming.transformChildren(element);
      element.acceptTree(this);
    }
  }

  private TreeElement next(TreeElement element, TreeElement root) {
    if (isDown) {
      TreeElement child = element.getFirstChildNode();
      if (child != null) return child;
    }
    else {
      isDown = true;
    }

    // up
    while (element != root) {
      TreeElement next = element.getTreeNext();
      if (next != null) return next;
      element = element.getTreeParent();
    }
    return null;
  }

  protected abstract boolean visitNode(TreeElement element);
}