package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;

import javax.swing.*;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.HashSet;
import java.util.Set;

/**
 * @author max
 */
public class RefElementNode extends InspectionTreeNode {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ui.RefElementNode");
  private boolean myHasDescriptorsUnder = false;
  private CommonProblemDescriptor mySingleDescriptor = null;

  public RefElementNode(RefElement element) {
    super(element);
    LOG.assertTrue(element != null);
  }

  public boolean hasDescriptorsUnder() { return myHasDescriptorsUnder; }

  public RefElement getElement() {
    return (RefElement)getUserObject();
  }

  public Icon getIcon(boolean expanded) {
    final PsiElement element = getElement().getElement();
    return element != null ? element.getIcon(Iconable.ICON_FLAG_VISIBILITY) : null;
  }

  public boolean isWritable() {
    final PsiElement element = getElement().getElement();
    return element == null || element.isWritable();
  }

  public int getProblemCount() {
    return Math.max(1, super.getProblemCount());
  }

  public String toString() {
    final RefElement element = getElement();
    if (element instanceof RefImplicitConstructor) {
      return RefUtil.getInstance().getQualifiedName(((RefImplicitConstructor)element).getOwnerClass());
    }
    return RefUtil.getInstance().getQualifiedName(element);
  }

  public boolean isValid() {
    return getElement().isValid();
  }

  public void add(MutableTreeNode newChild) {
    super.add(newChild);
    if (newChild instanceof ProblemDescriptionNode) {
      myHasDescriptorsUnder = true;
    }
  }

  public void setProblem(CommonProblemDescriptor descriptor) {
    mySingleDescriptor = descriptor;
  }

  public CommonProblemDescriptor getProblem() {
    return mySingleDescriptor;
  }

  public Set<RefElement> getPossibleChildren(RefElement refElement) {
    final TreeNode[] pathToRoot = getPath();

    final HashSet<RefElement> newChildren = new HashSet<RefElement>();

    if (!refElement.isValid()) return newChildren;

    for (RefElement refCallee : refElement.getOutReferences()) {
      if (((RefElementImpl)refCallee).isSuspicious()) {
        if (notInPath(pathToRoot, refCallee)) newChildren.add(refCallee);
      }
    }

    if (refElement instanceof RefMethod) {
      RefMethod refMethod = (RefMethod) refElement;

      if (!refMethod.isStatic() && !refMethod.isConstructor() && !refMethod.getOwnerClass().isAnonymous()) {
        for (RefMethod refDerived : refMethod.getDerivedMethods()) {
          if (((RefMethodImpl)refDerived).isSuspicious()) {
            if (notInPath(pathToRoot, refDerived)) newChildren.add(refDerived);
          }
        }
      }
    } else if (refElement instanceof RefClass) {
      RefClass refClass = (RefClass) refElement;
      for (RefClass subClass : refClass.getSubClasses()) {
        if ((subClass.isInterface() || subClass.isAbstract()) && ((RefClassImpl)subClass).isSuspicious()) {
          if (notInPath(pathToRoot, subClass)) newChildren.add(subClass);
        }
      }

      if (refClass.getDefaultConstructor() != null && refClass.getDefaultConstructor() instanceof RefImplicitConstructor) {
        Set<RefElement> fromConstructor = getPossibleChildren(refClass.getDefaultConstructor());
        newChildren.addAll(fromConstructor);
      }
    }

    return newChildren;
  }

  private boolean notInPath(TreeNode[] pathToRoot, RefElement refChild) {
    for (TreeNode aPathToRoot : pathToRoot) {
      InspectionTreeNode node = (InspectionTreeNode)aPathToRoot;
      if (node instanceof RefElementNode && ((RefElementNode)node).getElement() == refChild) return false;
    }

    return true;
  }
}
