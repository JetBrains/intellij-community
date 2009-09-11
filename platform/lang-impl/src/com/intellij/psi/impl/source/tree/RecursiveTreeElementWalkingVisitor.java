package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.psi.WalkingState;

public abstract class RecursiveTreeElementWalkingVisitor extends TreeElementVisitor{
  private final boolean myDoTransform;

  protected RecursiveTreeElementWalkingVisitor() {
    this(true);
  }

  protected RecursiveTreeElementWalkingVisitor(boolean doTransform) {
    myDoTransform = doTransform;
  }

  private static class ASTTreeGuide implements WalkingState.TreeGuide<ASTNode> {
    public ASTNode getNextSibling(ASTNode element) {
      return element.getTreeNext();
    }

    public ASTNode getPrevSibling(ASTNode element) {
      return element.getTreePrev();
    }

    public ASTNode getFirstChild(ASTNode element) {
      return element.getFirstChildNode();
    }

    public ASTNode getParent(ASTNode element) {
      return element.getTreeParent();
    }

    private static final ASTTreeGuide instance = new ASTTreeGuide();
  }

  private final WalkingState<ASTNode> myWalkingState = new WalkingState<ASTNode>(ASTTreeGuide.instance) {
    @Override
    public void elementFinished(ASTNode element) {

    }

    @Override
    public void visit(ASTNode element) {
      ((TreeElement)element).acceptTree(RecursiveTreeElementWalkingVisitor.this);
    }
  };

  @Override
  public void visitLeaf(LeafElement leaf) {
    visitNode(leaf);
  }

  @Override
  public void visitComposite(CompositeElement composite) {
    visitNode(composite);
  }

  protected void visitNode(TreeElement element){
    if (myDoTransform || !TreeUtil.isCollapsedChameleon(element)) {
      myWalkingState.elementStarted(element);
    }
  }
}
