/*
 * @author max
 */
package com.intellij.psi.impl.source.text;

import com.intellij.lang.ASTNode;
import com.intellij.pom.PomManager;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.impl.ChangeInfoImpl;
import com.intellij.pom.tree.events.impl.ReplaceChangeInfoImpl;
import com.intellij.pom.tree.events.impl.TreeChangeEventImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.util.diff.DiffTreeChangeBuilder;

public class ASTDiffBuilder implements DiffTreeChangeBuilder<ASTNode, ASTNode> {
  private final TreeChangeEventImpl myEvent;
  private final PsiFileImpl myFile;
  private final PsiManagerEx myPsiManager;
  private final boolean myIsPhysicalScope;


  public ASTDiffBuilder(final PsiFileImpl fileImpl) {
    myFile = fileImpl;
    myIsPhysicalScope = fileImpl.isPhysical();
    myPsiManager = (PsiManagerEx)fileImpl.getManager();
    myEvent = new TreeChangeEventImpl(PomManager.getModel(fileImpl.getProject()).getModelAspect(TreeAspect.class), fileImpl.getTreeElement());
  }

  public void nodeReplaced(ASTNode oldNode, ASTNode newNode) {
    if (oldNode instanceof FileElement && newNode instanceof FileElement) {
      BlockSupportImpl.replaceFileElement(myFile, (FileElement)oldNode, (FileElement)newNode, myPsiManager);
    }
    else {
      TreeUtil.ensureParsed(oldNode);
      transformNewChameleon(oldNode, newNode);

      ((TreeElement)newNode).rawRemove();
      ((TreeElement)oldNode).rawReplaceWithList((TreeElement)newNode);

      final ReplaceChangeInfoImpl change = (ReplaceChangeInfoImpl)ChangeInfoImpl.create(ChangeInfo.REPLACE, newNode);

      change.setReplaced(oldNode);
      myEvent.addElementaryChange(newNode, change);
      ((TreeElement)newNode).clearCaches();
      if (!(newNode instanceof FileElement)) {
        ((CompositeElement)newNode.getTreeParent()).subtreeChanged();
      }
      //System.out.println("REPLACED: " + oldNode + " to " + newNode);
    }
  }

  private static void transformNewChameleon(final ASTNode oldNode, ASTNode newNode) {
    if (newNode instanceof LazyParseableElement) {
      final FileElement dummyRoot = new DummyHolder(
          oldNode.getPsi().getManager(),
          oldNode.getPsi().getContainingFile(),
          SharedImplUtil.findCharTableByTree(oldNode)
      ).getTreeElement();
      dummyRoot.rawAddChildren((TreeElement)newNode);
      TreeUtil.ensureParsed(newNode);
    }
  }

  public void nodeDeleted(ASTNode parent, final ASTNode child) {
    PsiElement psiParent = parent.getPsi();
    PsiElement psiChild = myIsPhysicalScope ? child.getPsi() : null;

    PsiTreeChangeEventImpl event = null;
    if (psiParent != null && psiChild != null) {
      event = new PsiTreeChangeEventImpl(myPsiManager);
      event.setParent(psiParent);
      event.setChild(psiChild);
      myPsiManager.beforeChildRemoval(event);
    }

    myEvent.addElementaryChange(child, ChangeInfoImpl.create(ChangeInfo.REMOVED, child));
    ((TreeElement)child).rawRemove();
    ((CompositeElement)parent).subtreeChanged();

    /*if (event != null) {
      myPsiManager.childRemoved(event);
    }*/

    //System.out.println("DELETED from " + parent + ": " + child);
  }

  public void nodeInserted(final ASTNode oldParent, ASTNode node, final int pos) {
    transformNewChameleon(oldParent, node);

    ASTNode anchor = null;
    for (int i = 0; i < pos; i++) {
      if (anchor == null) {
        anchor = oldParent.getFirstChildNode();
      }
      else {
        anchor = anchor.getTreeNext();
      }
    }

    ((TreeElement)node).rawRemove();
    if (anchor != null) {
      ((TreeElement)anchor).rawInsertAfterMe((TreeElement)node);
    }
    else {
      if (oldParent.getFirstChildNode() != null) {
        ((TreeElement)oldParent.getFirstChildNode()).rawInsertBeforeMe((TreeElement)node);
      }
      else {
        ((CompositeElement)oldParent).rawAddChildren((TreeElement)node);
      }
    }

    myEvent.addElementaryChange(node, ChangeInfoImpl.create(ChangeInfo.ADD, node));
    ((TreeElement)node).clearCaches();
    ((CompositeElement)oldParent).subtreeChanged();

    //System.out.println("INSERTED to " + oldParent + ": " + node + " at " + pos);
  }

  public TreeChangeEventImpl getEvent() {
    return myEvent;
  }
}
