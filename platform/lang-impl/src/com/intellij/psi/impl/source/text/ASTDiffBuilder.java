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
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.util.diff.DiffTreeChangeBuilder;
import org.jetbrains.annotations.NotNull;

public class ASTDiffBuilder implements DiffTreeChangeBuilder<ASTNode, ASTNode> {
  public static boolean DEBUG = false;
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

  public void nodeReplaced(@NotNull ASTNode oldNode, @NotNull ASTNode newNode) {
    if (oldNode instanceof FileElement && newNode instanceof FileElement) {
      BlockSupportImpl.replaceFileElement(myFile, (FileElement)oldNode, (FileElement)newNode, myPsiManager);
    }
    else {
      final ASTNode parent = oldNode.getTreeParent();

      TreeUtil.ensureParsed(oldNode);

      ((TreeElement)newNode).rawRemove();
      ((TreeElement)oldNode).rawReplaceWithList((TreeElement)newNode);

      final ReplaceChangeInfoImpl change = (ReplaceChangeInfoImpl)ChangeInfoImpl.create(ChangeInfo.REPLACE, newNode);

      change.setReplaced(oldNode);
      myEvent.addElementaryChange(newNode, change);
      ((TreeElement)newNode).clearCaches();
      if (!(newNode instanceof FileElement)) {
        ((CompositeElement)newNode.getTreeParent()).subtreeChanged();
      }

      if (DEBUG) {
        DebugUtil.checkTreeStructure(parent);
      }
    }
  }

  public void nodeDeleted(@NotNull ASTNode parent, @NotNull final ASTNode child) {
    PsiElement psiParent = parent.getPsi();
    PsiElement psiChild = myIsPhysicalScope ? child.getPsi() : null;

    if (psiParent != null && psiChild != null) {
      PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myPsiManager);
      event.setParent(psiParent);
      event.setChild(psiChild);
      myPsiManager.beforeChildRemoval(event);
    }

    myEvent.addElementaryChange(child, ChangeInfoImpl.create(ChangeInfo.REMOVED, child));
    ((TreeElement)child).rawRemove();
    ((CompositeElement)parent).subtreeChanged();

    if (DEBUG) {
      DebugUtil.checkTreeStructure(parent);
    }
  }

  public void nodeInserted(@NotNull final ASTNode oldParent, @NotNull ASTNode node, final int pos) {
    ASTNode anchor = null;
    for (int i = 0; i < pos; i++) {
      anchor = anchor == null ? oldParent.getFirstChildNode() : anchor.getTreeNext();
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

    if (DEBUG) {
      DebugUtil.checkTreeStructure(oldParent);
    }
  }

  public TreeChangeEventImpl getEvent() {
    return myEvent;
  }
}
