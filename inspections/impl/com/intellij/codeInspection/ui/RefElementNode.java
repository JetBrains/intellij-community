package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;

import javax.swing.*;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.*;

/**
 * @author max
 */
public class RefElementNode extends InspectionTreeNode {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ui.RefElementNode");
  private boolean myHasDescriptorsUnder = false;
  private ProblemDescriptor mySingleDescriptor = null;

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
      return RefUtil.getQualifiedName(((RefImplicitConstructor)element).getOwnerClass());
    }
    return RefUtil.getQualifiedName(element);
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

  public void setProblem(ProblemDescriptor descriptor) {
    mySingleDescriptor = descriptor;
  }

  public ProblemDescriptor getProblem() {
    return mySingleDescriptor;
  }

  public Set<RefElement> getPossibleChildren(RefElement refElement) {
    final TreeNode[] pathToRoot = getPath();

    final HashSet<RefElement> newChildren = new HashSet<RefElement>();

    if (!refElement.isValid()) return newChildren;

    Iterator<RefElement> outReferences = refElement.getOutReferences().iterator();
    while (outReferences.hasNext()) {
      RefElement refCallee = outReferences.next();
      if (refCallee.isSuspicious()) {
        if (notInPath(pathToRoot, refCallee)) newChildren.add(refCallee);
      }
    }

    if (refElement instanceof RefMethod) {
      RefMethod refMethod = (RefMethod) refElement;

      if (!refMethod.isStatic() && !refMethod.isConstructor() && !refMethod.getOwnerClass().isAnonymous()) {
        for (Iterator<RefMethod> iterator = refMethod.getDerivedMethods().iterator(); iterator.hasNext();) {
          RefMethod refDerived = iterator.next();
          if (refDerived.isSuspicious()) {
            if (notInPath(pathToRoot, refDerived)) newChildren.add(refDerived);
          }
        }
      }
    } else if (refElement instanceof RefClass) {
      RefClass refClass = (RefClass) refElement;
      for (Iterator<RefClass> iterator = refClass.getSubClasses().iterator(); iterator.hasNext();) {
        RefClass subClass = iterator.next();
        if ((subClass.isInterface() || subClass.isAbstract()) && subClass.isSuspicious()) {
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
    for (int i = 0; i < pathToRoot.length; i++) {
      InspectionTreeNode node = (InspectionTreeNode) pathToRoot[i];
      if (node instanceof RefElementNode && ((RefElementNode)node).getElement() == refChild) return false;
    }

    return true;
  }
}
