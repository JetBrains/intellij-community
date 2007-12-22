package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.PsiElement;
import com.intellij.util.IconUtil;
import com.intellij.util.Icons;
import org.jetbrains.annotations.Nullable;

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
  protected InspectionTool myTool;

  public RefElementNode(final Object userObject, final InspectionTool tool) {
    super(userObject);
    myTool = tool;
  }

  public RefElementNode(RefElement element, final InspectionTool inspectionTool) {
    super(element);
    myTool = inspectionTool;
    LOG.assertTrue(element != null);
  }

  public boolean hasDescriptorsUnder() { return myHasDescriptorsUnder; }

  @Nullable
  public RefEntity getElement() {
    return (RefEntity)getUserObject();
  }

  @Nullable
  public Icon getIcon(boolean expanded) {
    final RefEntity refEntity = getElement();
    if (refEntity == null) {
      return null;
    }
    if (refEntity instanceof RefElement) {
      final RefElement refElement = (RefElement)refEntity;
      final PsiElement element = refElement.getElement();
      if (element != null) {
        final int flags = Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS;
        if (refElement instanceof RefJavaElement && ((RefJavaElement)refElement).isSyntheticJSP()) {
          return IconUtil.getIcon(element.getContainingFile().getVirtualFile(),
                                  flags,
                                  element.getProject());
        }
        return element.getIcon(flags);
      }
      else {
        return null;
      }
    } else if (refEntity instanceof RefPackage) {
      return expanded ? Icons.PACKAGE_OPEN_ICON : Icons.PACKAGE_ICON;
    } else if (refEntity instanceof RefModule) {
      return ((RefModule)refEntity).getModule().getModuleType().getNodeIcon(expanded);
    } else if (refEntity instanceof RefProject) {
      return Icons.PROJECT_ICON;
    }
    return null;
  }

  public int getProblemCount() {
    return Math.max(1, super.getProblemCount());
  }

  public String toString() {
    final RefEntity element = getElement();
    if (element == null) {
      return "";
    }
    if (element instanceof RefImplicitConstructor) {
      return RefUtil.getInstance().getQualifiedName(((RefImplicitConstructor)element).getOwnerClass());
    }
    return RefUtil.getInstance().getQualifiedName(element);
  }

  public boolean isValid() {
    final RefEntity refEntity = getElement();
    return refEntity != null && refEntity.isValid();
  }

  public boolean isResolved() {
    return myTool.isElementIgnored(getElement());
  }


  public void ignoreElement() {
    myTool.ignoreCurrentElement(getElement());
    super.ignoreElement();
  }

  public void amnesty() {
    myTool.amnesty(getElement());
    super.amnesty();
  }

  public FileStatus getNodeStatus() {
    return  myTool.getElementStatus(getElement());    
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

      if (refClass.getDefaultConstructor() instanceof RefImplicitConstructor) {
        Set<RefElement> fromConstructor = getPossibleChildren(refClass.getDefaultConstructor());
        newChildren.addAll(fromConstructor);
      }
    }

    return newChildren;
  }

  private static boolean notInPath(TreeNode[] pathToRoot, RefElement refChild) {
    for (TreeNode aPathToRoot : pathToRoot) {
      InspectionTreeNode node = (InspectionTreeNode)aPathToRoot;
      if (node instanceof RefElementNode && ((RefElementNode)node).getElement() == refChild) return false;
    }

    return true;
  }
}
