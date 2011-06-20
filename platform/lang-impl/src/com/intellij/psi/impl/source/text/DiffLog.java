/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.text;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.TreeAspectEvent;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.diff.DiffTreeChangeBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: cdr
 */
public class DiffLog implements DiffTreeChangeBuilder<ASTNode,ASTNode> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.text.DiffLog");

  public DiffLog() {
  }

  private abstract static class LogEntry {
    protected LogEntry() {
      ProgressManager.checkCanceled();
    }
    abstract void doActualPsiChange(@NotNull PsiFile file, @NotNull ASTDiffBuilder astDiffBuilder);
  }

  private final List<LogEntry> myEntries = new ArrayList<LogEntry>();

  public void doActualPsiChange(final PsiFile file){
    try {


      final Document document = file.getViewProvider().getDocument();
      PsiToDocumentSynchronizer.DocumentChangeTransaction transaction =
        ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(file.getProject())).getSynchronizer().getTransaction(document);

      final PsiFileImpl fileImpl = (PsiFileImpl)file;

      final ASTDiffBuilder astDiffBuilder = new ASTDiffBuilder(fileImpl);

      if (transaction == null) {
      final PomModel model = PomManager.getModel(fileImpl.getProject());

      model.runTransaction(new PomTransactionBase(fileImpl, model.getModelAspect(TreeAspect.class)) {
        public PomModelEvent runInner() {
          for (LogEntry entry : myEntries) {
            entry.doActualPsiChange(file, astDiffBuilder);
          }
          fileImpl.subtreeChanged();

          final PomModel model = PomManager.getModel(fileImpl.getProject());
          TreeAspectEvent treeAspectEvent = new TreeAspectEvent(model, astDiffBuilder.getEvent());
          return treeAspectEvent;
        }
      });
      }
      else {
        for (LogEntry entry : myEntries) {
          entry.doActualPsiChange(file, astDiffBuilder);
        }
        fileImpl.subtreeChanged();
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }


  }

  @Override
  public void nodeReplaced(@NotNull ASTNode oldNode, @NotNull ASTNode newNode) {
    if (oldNode instanceof FileElement && newNode instanceof FileElement) {
      appendReplaceFileElement((FileElement)oldNode, (FileElement)newNode);
    }
    else {
      myEntries.add(new ReplaceEntry(oldNode, newNode));
    }
  }

  public void appendReplaceElementWithEvents(CompositeElement oldRoot, CompositeElement newRoot) {
    myEntries.add(new ReplaceElementWithEvents(oldRoot, newRoot));
  }

  public void appendReplaceFileElement(FileElement oldNode, FileElement newNode) {
    myEntries.add(new ReplaceFileElement(oldNode, newNode));
  }

  @Override
  public void nodeDeleted(@NotNull ASTNode oldParent, @NotNull ASTNode oldNode) {
    myEntries.add(new DeleteEntry(oldParent, oldNode));

  }

  @Override
  public void nodeInserted(@NotNull ASTNode oldParent, @NotNull ASTNode newNode, int pos) {
    myEntries.add(new InsertEntry(oldParent, newNode, pos));
  }

  private static class ReplaceEntry extends LogEntry {
    private final ASTNode myOldChild;
    private final ASTNode myNewChild;

    public ReplaceEntry(ASTNode oldChild, ASTNode newChild) {
      myOldChild = oldChild;
      myNewChild = newChild;
    }

    @Override
    void doActualPsiChange(@NotNull PsiFile file, @NotNull ASTDiffBuilder astDiffBuilder) {
      ASTNode oldNode = myOldChild;
      ASTNode parent = oldNode.getTreeParent();
      ASTNode newNode = myNewChild;
      assert parent != null : "old:" + oldNode + " new:" + newNode;

      final PsiElement psiParent = parent.getPsi();
      final PsiElement psiChild = file.isPhysical() ? oldNode.getPsi() : null;
      if (psiParent != null && psiChild != null) {
        final PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(file.getManager());
        event.setParent(psiParent);
        event.setChild(psiChild);
        ((PsiManagerEx)file.getManager()).beforeChildReplacement(event);
      }

      ((TreeElement)newNode).rawRemove();
      ((TreeElement)oldNode).rawReplaceWithList((TreeElement)newNode);


       astDiffBuilder.nodeReplaced(oldNode, newNode);

      /////////////////
      ((TreeElement)newNode).clearCaches();
      if (!(newNode instanceof FileElement)) {
        ((CompositeElement)newNode.getTreeParent()).subtreeChanged();
      }

      DebugUtil.checkTreeStructure(parent);

    }
  }

  private static class DeleteEntry extends LogEntry {
    private final ASTNode myOldParent;
    private final ASTNode myOldNode;

    public DeleteEntry(ASTNode oldParent, ASTNode oldNode) {
      myOldParent = oldParent;
      myOldNode = oldNode;
    }

    @Override
    void doActualPsiChange(@NotNull PsiFile file, @NotNull ASTDiffBuilder astDiffBuilder) {
      ASTNode child = myOldNode;
      ASTNode parent = myOldParent;

      PsiElement psiParent = parent.getPsi();
      PsiElement psiChild = file.isPhysical() ? child.getPsi() : null;

      if (psiParent != null && psiChild != null) {
        PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(file.getManager());
        event.setParent(psiParent);
        event.setChild(psiChild);
        ((PsiManagerEx)file.getManager()).beforeChildRemoval(event);
      }

      astDiffBuilder.nodeDeleted(parent, child);

      ((TreeElement)child).rawRemove();
      ((CompositeElement)parent).subtreeChanged();

      DebugUtil.checkTreeStructure(parent);

    }
  }

  private static class InsertEntry extends LogEntry {
    private final ASTNode myOldParent;
    private final ASTNode myNewNode;
    private final int myPos;

    public InsertEntry(ASTNode oldParent, ASTNode newNode, int pos) {
      myOldParent = oldParent;
      myNewNode = newNode;
      myPos = pos;
    }

    @Override
    void doActualPsiChange(@NotNull PsiFile file, @NotNull ASTDiffBuilder astDiffBuilder) {
      ASTNode anchor = null;
      ASTNode firstChildNode = myOldParent.getFirstChildNode();
      for (int i = 0; i < myPos; i++) {
        anchor = anchor == null ? firstChildNode : anchor.getTreeNext();
      }

      ((TreeElement)myNewNode).rawRemove();
      if (anchor != null) {
        ((TreeElement)anchor).rawInsertAfterMe((TreeElement)myNewNode);
      }
      else {
        if (firstChildNode != null) {
          ((TreeElement)firstChildNode).rawInsertBeforeMe((TreeElement)myNewNode);
        }
        else {
          ((CompositeElement)myOldParent).rawAddChildren((TreeElement)myNewNode);
        }
      }

      astDiffBuilder.nodeInserted(myOldParent, myNewNode, myPos);

      ((TreeElement)myNewNode).clearCaches();
      ((CompositeElement)myOldParent).subtreeChanged();

      DebugUtil.checkTreeStructure(myOldParent);
    }
  }

  private static class ReplaceFileElement extends LogEntry {
    private final FileElement myOldNode;
    private final FileElement myNewNode;

    public ReplaceFileElement(FileElement oldNode, FileElement newNode) {
      myOldNode = oldNode;
      myNewNode = newNode;
    }

    @Override
    void doActualPsiChange(@NotNull PsiFile file, @NotNull ASTDiffBuilder astDiffBuilder) {
      PsiFileImpl fileImpl = (PsiFileImpl)file;
      final int oldLength = myOldNode.getTextLength();
      BlockSupportImpl.sendPsiBeforeEvent(fileImpl);
      if (myOldNode.getFirstChildNode() != null) myOldNode.rawRemoveAllChildren();
      final ASTNode firstChildNode = myNewNode.getFirstChildNode();
      if (firstChildNode != null) myOldNode.rawAddChildren((TreeElement)firstChildNode);
      fileImpl.getTreeElement().setCharTable(myNewNode.getCharTable());
      ((PsiManagerEx)file.getManager()).invalidateFile(fileImpl);
      myOldNode.subtreeChanged();
      BlockSupportImpl.sendPsiAfterFileEvent(fileImpl, oldLength);
    }
  }

  private static class ReplaceElementWithEvents extends LogEntry {
    private final CompositeElement myOldRoot;
    private final CompositeElement myNewRoot;

    public ReplaceElementWithEvents(CompositeElement oldRoot, CompositeElement newRoot) {
      myOldRoot = oldRoot;
      myNewRoot = newRoot;
    }

    @Override
    void doActualPsiChange(@NotNull PsiFile file, @NotNull ASTDiffBuilder astDiffBuilder) {
      myOldRoot.replaceAllChildrenToChildrenOf(myNewRoot);
    }
  }
}
