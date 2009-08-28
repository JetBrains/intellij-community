package com.intellij.psi.impl.source.tree;

public abstract class RecursiveTreeElementWalkingVisitor extends TreeElementVisitor{
  private boolean startedWalking;
  private boolean isDown;
  private final boolean myDoTransform;

  protected RecursiveTreeElementWalkingVisitor() {
    this(true);
  }
  protected RecursiveTreeElementWalkingVisitor(boolean doTransform) {
    myDoTransform = doTransform;
  }

  @Override public void visitLeaf(LeafElement leaf) {
    visitNode(leaf);
  }

  @Override public void visitComposite(CompositeElement composite) {
    isDown = visitNode(composite);
    if (!startedWalking) {
      startedWalking = true;
      if (myDoTransform || !TreeUtil.isCollapsedChameleon(composite)) {
        walk(composite);
      }
      startedWalking = false;
    }
  }

  private void walk(TreeElement root) {
    for (TreeElement element = next(root, root); element != null; element = next(element, root)) {
      CompositeElement parent = element.getTreeParent();
      TreeElement next = element.getTreeNext();
      isDown = false; // if client visitor did not call default visitElement it means skip subtree
      element.acceptTree(this);
      assert element.getTreeNext() == next;
      assert element.getTreeParent() == parent;
    }
  }

  private TreeElement next(TreeElement element, TreeElement root) {
    if (isDown) {
      TreeElement child = element.getFirstChildNode();
      if (child != null) return child;
    }

    // up
    while (element != root) {
      TreeElement next = element.getTreeNext();
      if (next != null) {
        assert next.getTreePrev() == element : "Element: "+element+"; next.prev: "+next.getTreePrev()+"; File: "+ SharedImplUtil.getContainingFile(element);
        return next;
      }
      element = element.getTreeParent();
    }
    return null;
  }

  protected boolean visitNode(TreeElement element){
    return true;
  }
}
