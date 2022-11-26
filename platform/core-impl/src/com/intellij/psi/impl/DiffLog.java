// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.impl.TreeChangeEventImpl;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.util.diff.DiffTreeChangeBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class DiffLog implements DiffTreeChangeBuilder<ASTNode,ASTNode> {
  public DiffLog() { }

  private abstract static class LogEntry {
    LogEntry() {
      ProgressIndicatorProvider.checkCanceled();
    }
    abstract void doActualPsiChange(@NotNull PsiFile file, @NotNull TreeChangeEventImpl event);
  }

  private final List<LogEntry> myEntries = new ArrayList<>();

  @NotNull
  public TreeChangeEventImpl performActualPsiChange(@NotNull PsiFile file) {
    TreeAspect modelAspect = PomManager.getModel(file.getProject()).getModelAspect(TreeAspect.class);
    TreeChangeEventImpl event = new TreeChangeEventImpl(modelAspect, file.getNode());
    for (LogEntry entry : myEntries) {
      entry.doActualPsiChange(file, event);
    }
    file.subtreeChanged();
    return event;
  }

  @Override
  public void nodeReplaced(@NotNull ASTNode oldNode, @NotNull ASTNode newNode) {
    if (oldNode instanceof FileASTNode && newNode instanceof FileASTNode) {
      appendReplaceFileElement((FileASTNode)oldNode, (FileASTNode)newNode);
    }
    else {
      myEntries.add(new ReplaceEntry(oldNode, newNode));
    }
  }

  void appendReplaceElementWithEvents(@NotNull CompositeElement oldRoot, @NotNull CompositeElement newRoot) {
    myEntries.add(new ReplaceElementWithEvents(oldRoot, newRoot));
  }

  void appendReplaceFileElement(@NotNull FileASTNode oldNode, @NotNull FileASTNode newNode) {
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

  private static final class ReplaceEntry extends LogEntry {
    private final ASTNode myOldChild;
    private final ASTNode myNewChild;

    private ReplaceEntry(@NotNull ASTNode oldNode, @NotNull ASTNode newNode) {
      myOldChild = oldNode;
      myNewChild = newNode;
      ensureOldParent();
    }

    @Override
    void doActualPsiChange(@NotNull PsiFile file, @NotNull TreeChangeEventImpl changeEvent) {
      ASTNode parent = ensureOldParent();

      PsiElement psiParent = parent.getPsi();
      PsiElement psiOldChild = file.isPhysical() ? myOldChild.getPsi() : null;
      if (psiParent != null && psiOldChild != null) {
        PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(file.getManager());
        event.setParent(psiParent);
        event.setFile(file);
        event.setOldChild(psiOldChild);
        PsiElement psiNewChild = getPsi(myNewChild, file);
        event.setNewChild(psiNewChild);
        ((PsiManagerEx)file.getManager()).beforeChildReplacement(event);
      }

      if (!(myOldChild instanceof FileElement) || !(myNewChild instanceof FileElement)) {
        changeEvent.addElementaryChange(myOldChild.getTreeParent());
      }
      ((ReparseableASTNode) myOldChild).applyReplaceOnReparse(myNewChild);

      DebugUtil.checkTreeStructure(parent);
    }

    @NotNull
    private ASTNode ensureOldParent() {
      ASTNode parent = myOldChild.getTreeParent();
      if (parent == null) {
        throw PsiInvalidElementAccessException.createByNode(myOldChild, "new:" + myNewChild);
      }
      return parent;
    }
  }

  private static final class DeleteEntry extends LogEntry {
    @NotNull private final ASTNode myOldParent;
    @NotNull private final ASTNode myOldNode;

    private DeleteEntry(@NotNull ASTNode oldParent, @NotNull ASTNode oldNode) {
      myOldParent = oldParent;
      myOldNode = oldNode;
    }

    @Override
    void doActualPsiChange(@NotNull PsiFile file, @NotNull TreeChangeEventImpl changeEvent) {
      PsiElement psiParent = myOldParent.getPsi();
      PsiElement psiChild = file.isPhysical() ? myOldNode.getPsi() : null;

      if (psiParent != null && psiChild != null) {
        PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(file.getManager());
        event.setParent(psiParent);
        event.setChild(psiChild);
        event.setFile(file);
        ((PsiManagerEx)file.getManager()).beforeChildRemoval(event);
      }

      changeEvent.addElementaryChange(myOldParent);
      ((ReparseableASTNode) myOldParent).applyDeleteOnReparse(myOldNode);

      DebugUtil.checkTreeStructure(myOldParent);
    }
  }

  private static final class InsertEntry extends LogEntry {
    @NotNull private final ASTNode myOldParent;
    @NotNull private final ASTNode myNewNode;
    private final int myPos;

    private InsertEntry(@NotNull ASTNode oldParent, @NotNull ASTNode newNode, int pos) {
      assert pos>=0 : pos;
      myOldParent = oldParent;
      myNewNode = newNode;
      myPos = pos;
    }

    @Override
    void doActualPsiChange(@NotNull PsiFile file, @NotNull TreeChangeEventImpl changeEvent) {
      ASTNode anchor = null;
      ASTNode firstChildNode = myOldParent.getFirstChildNode();
      for (int i = 0; i < myPos; i++) {
        anchor = anchor == null ? firstChildNode : anchor.getTreeNext();
      }

      PsiElement psiParent = myOldParent.getPsi();
      PsiElement psiChild = getPsi(myNewNode, file);
      if (psiParent != null && psiChild != null) {
        PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(file.getManager());
        event.setParent(psiParent);
        event.setChild(psiChild);
        event.setFile(file);
        ((PsiManagerEx)file.getManager()).beforeChildAddition(event);
      }

      changeEvent.addElementaryChange(myOldParent);

      ((ReparseableASTNode) myOldParent).applyInsertOnReparse(myNewNode, anchor);

      DebugUtil.checkTreeStructure(myOldParent);
    }
  }

  private static PsiElement getPsi(ASTNode node, PsiFile file) {
    node.putUserData(TreeUtil.CONTAINING_FILE_KEY_AFTER_REPARSE, ((PsiFileImpl)file).getTreeElement());
    PsiElement psiChild = file.isPhysical() ? node.getPsi() : null;
    node.putUserData(TreeUtil.CONTAINING_FILE_KEY_AFTER_REPARSE, null);
    return psiChild;
  }

  private static final class ReplaceFileElement extends LogEntry {
    @NotNull private final FileASTNode myOldNode;
    @NotNull private final FileASTNode myNewNode;

    private ReplaceFileElement(@NotNull FileASTNode oldNode, @NotNull FileASTNode newNode) {
      myOldNode = oldNode;
      myNewNode = newNode;
    }

    @Override
    void doActualPsiChange(@NotNull PsiFile file, @NotNull TreeChangeEventImpl event) {
      event.addElementaryChange(myOldNode);
      ((ReparseableASTNode) myOldNode).applyReplaceFileOnReparse(file, myNewNode);
    }
  }

  private static final class ReplaceElementWithEvents extends LogEntry {
    @NotNull private final CompositeElement myOldRoot;
    @NotNull private final CompositeElement myNewRoot;

    private ReplaceElementWithEvents(@NotNull CompositeElement oldRoot, @NotNull CompositeElement newRoot) {
      myOldRoot = oldRoot;
      myNewRoot = newRoot;
      // parse in background to reduce time spent in EDT and to ensure the newRoot light containing file is still valid
      TreeUtil.ensureParsed(myOldRoot.getFirstChildNode());
      TreeUtil.ensureParsed(myNewRoot.getFirstChildNode());
    }

    @Override
    void doActualPsiChange(@NotNull PsiFile file, @NotNull TreeChangeEventImpl event) {
      myOldRoot.replaceAllChildrenToChildrenOf(myNewRoot);
    }
  }

  public void doActualPsiChange(@NotNull PsiFile file) {
    CodeStyleManager.getInstance(file.getProject()).performActionWithFormatterDisabled((Runnable)() -> {
      FileViewProvider viewProvider = file.getViewProvider();
      synchronized (((AbstractFileViewProvider)viewProvider).getFilePsiLock()) {
        viewProvider.beforeContentsSynchronized();

        Document document = viewProvider.getDocument();
        PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(file.getProject());
        PsiToDocumentSynchronizer.DocumentChangeTransaction transaction = documentManager.getSynchronizer().getTransaction(document);

        if (transaction == null) {
          PomModel model = PomManager.getModel(file.getProject());

          model.runTransaction(new PomTransactionBase(file) {
            @Override
            public @NotNull PomModelEvent runInner() {
              return new PomModelEvent(model, performActualPsiChange(file));
            }
          });
        }
        else {
          performActualPsiChange(file);
        }
      }
    });
  }
}
