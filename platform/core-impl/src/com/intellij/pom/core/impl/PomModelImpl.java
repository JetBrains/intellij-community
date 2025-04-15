// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.core.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.PomTransaction;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.wrappers.PsiEventWrapperAspect;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.lang.CompoundRuntimeException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PomModelImpl extends UserDataHolderBase implements PomModel {
  private final Project myProject;
  private final TreeAspect myTreeAspect;
  private final PsiEventWrapperAspect myPsiAspect;
  private final Collection<PomModelListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public PomModelImpl(@NotNull Project project) {
    myProject = project;
    myTreeAspect = TreeAspect.getInstance(project);
    myPsiAspect = new PsiEventWrapperAspect(myTreeAspect);
  }

  @Override
  public <T extends PomModelAspect> T getModelAspect(@NotNull Class<T> aClass) {
    //noinspection unchecked
    return myTreeAspect.getClass().equals(aClass) ? (T)myTreeAspect :
           myPsiAspect.getClass().equals(aClass) ? (T)myPsiAspect :
           null;
  }

  @Override
  public void addModelListener(@NotNull PomModelListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void addModelListener(final @NotNull PomModelListener listener, @NotNull Disposable parentDisposable) {
    addModelListener(listener);
    Disposer.register(parentDisposable, () -> removeModelListener(listener));
  }

  @Override
  public void removeModelListener(@NotNull PomModelListener listener) {
    myListeners.remove(listener);
  }

  @SuppressWarnings("SSBasedInspection")
  private final ThreadLocal<Stack<PomTransaction>> myTransactionStack = ThreadLocal.withInitial(Stack::new);

  @Override
  public void runTransaction(@NotNull PomTransaction transaction) throws IncorrectOperationException{
    if (!isAllowPsiModification()) {
      throw new IncorrectOperationException("Must not modify PSI inside save listener");
    }
    ProgressManager.getInstance().executeNonCancelableSection(() -> {
      PsiElement changeScope = transaction.getChangeScope();
      PsiFile containingFileByTree = getContainingFileByTree(changeScope);
      Document document = startTransaction(transaction, containingFileByTree);

      PomTransaction block = getBlockingTransaction(changeScope);
      if (block != null) {
        block.getAccumulatedEvent().beforeNestedTransaction();
      }

      List<Throwable> throwables = new ArrayList<>(0);
      DebugUtil.performPsiModification(null, ()->{
        try{
          Stack<PomTransaction> blockedAspects = myTransactionStack.get();
          blockedAspects.push(transaction);

          final PomModelEvent event;
          try{
            transaction.run();
            event = transaction.getAccumulatedEvent();
          }
          catch (ProcessCanceledException e) {
            throw e;
          }
          catch(Exception e){
            throwables.add(e);
            return;
          }
          finally{
            blockedAspects.pop();
          }
          if(block != null){
            block.getAccumulatedEvent().merge(event);
            return;
          }

          if (event.getChangedAspects().contains(myTreeAspect)) {
            updateDependentAspects(event);
          }

          for (final PomModelListener listener : myListeners) {
            final Set<PomModelAspect> changedAspects = event.getChangedAspects();
            for (PomModelAspect modelAspect : changedAspects) {
              if (listener.isAspectChangeInteresting(modelAspect)) {
                listener.modelChanged(event);
                break;
              }
            }
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable t) {
          throwables.add(t);
        }
        finally {
          try {
            if (containingFileByTree != null) {
              commitTransaction(containingFileByTree, document);
            }
          }
          catch (ProcessCanceledException e) {
            throw e;
          }
          catch (Throwable t) {
            throwables.add(t);
          }
          if (!throwables.isEmpty()) CompoundRuntimeException.throwIfNotEmpty(throwables);
        }
      });
    });
  }

  protected void updateDependentAspects(PomModelEvent event) {
    myPsiAspect.update(event);
  }

  private @Nullable PomTransaction getBlockingTransaction(PsiElement changeScope) {
    Stack<PomTransaction> blockedAspects = myTransactionStack.get();
    ListIterator<PomTransaction> iterator = blockedAspects.listIterator(blockedAspects.size());
    while (iterator.hasPrevious()) {
      PomTransaction transaction = iterator.previous();
      if (PsiTreeUtil.isAncestor(getContainingFileByTree(transaction.getChangeScope()), changeScope, false)) {
        return transaction;
      }
    }
    return null;
  }

  private void commitTransaction(@NotNull PsiFile containingFileByTree, @Nullable Document document) {
    final PsiDocumentManagerBase manager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject);
    final PsiToDocumentSynchronizer synchronizer = manager.getSynchronizer();

    boolean isFromCommit = manager.isCommitInProgress();
    boolean isPhysicalPsiChange = !isFromCommit && !synchronizer.isIgnorePsiEvents();
    if (isPhysicalPsiChange) {
      reparseParallelTrees(containingFileByTree, synchronizer);
    }

    boolean docSynced = false;
    if (document != null) {
      final int oldLength = containingFileByTree.getTextLength();
      docSynced = synchronizer.commitTransaction(document);
      if (docSynced) {
        sendAfterChildrenChangedEvent(containingFileByTree, oldLength);
      }
    }

    if (isPhysicalPsiChange && docSynced) {
      containingFileByTree.getViewProvider().contentsSynchronized();
    }

  }

  private void reparseParallelTrees(PsiFile changedFile, PsiToDocumentSynchronizer synchronizer) {
    List<PsiFile> allFiles = getAllFiles(changedFile);
    if (allFiles.size() <= 1) {
      return;
    }

    CharSequence newText = changedFile.getNode().getChars();
    for (final PsiFile file : allFiles) {
      FileElement fileElement = file == changedFile ? null : ((PsiFileImpl)file).getTreeElement();
      Runnable changeAction = fileElement == null ? null : reparseFile(file, fileElement, newText);
      if (changeAction == null) continue;

      synchronizer.setIgnorePsiEvents(true);
      try {
        CodeStyleManager.getInstance(file.getProject()).performActionWithFormatterDisabled(changeAction);
      }
      finally {
        synchronizer.setIgnorePsiEvents(false);
      }
    }
  }

  private static @NotNull List<PsiFile> getAllFiles(@NotNull PsiFile changedFile) {
    VirtualFile file = changedFile.getVirtualFile();
    if (file == null) {
      return changedFile.getViewProvider().getAllFiles();
    }
    FileManager fileManager = ((PsiManagerEx)changedFile.getManager()).getFileManager();
    List<FileViewProvider> providers = fileManager.findCachedViewProviders(file);
    return ContainerUtil.flatMap(providers, p -> p.getAllFiles());
  }

  /**
   * Reparses the file and returns a runnable which actually changes the PSI structure to match the new text.
   */
  @ApiStatus.Internal
  public @Nullable Runnable reparseFile(@NotNull PsiFile file, @NotNull FileElement treeElement, @NotNull CharSequence newText) {
    TextRange changedPsiRange = ChangedPsiRangeUtil.getChangedPsiRange(file, treeElement, newText);
    if (changedPsiRange == null) return null;

    ProgressIndicator indicator = EmptyProgressIndicator.notNullize(ProgressIndicatorProvider.getGlobalProgressIndicator());
    DiffLog log = BlockSupport.getInstance(myProject).reparseRange(file, treeElement, changedPsiRange, newText, indicator,
                                                                   treeElement.getText());
    return () -> runTransaction(new PomTransactionBase(file) {
      @Override
      public @NotNull PomModelEvent runInner() throws IncorrectOperationException {
        return new PomModelEvent(PomModelImpl.this, log.performActualPsiChange(file));
      }
    });
  }

  @Contract("_,null -> null")
  private @Nullable Document startTransaction(@NotNull PomTransaction transaction, @Nullable PsiFile psiFile) {
    final PsiDocumentManagerBase manager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject);
    final PsiToDocumentSynchronizer synchronizer = manager.getSynchronizer();
    final PsiElement changeScope = transaction.getChangeScope();

    if (psiFile != null && !(psiFile instanceof DummyHolder) && !manager.isCommitInProgress()) {
      PsiUtilCore.ensureValid(psiFile);
    }

    boolean physical = changeScope.isPhysical();
    if (synchronizer.toProcessPsiEvent()) {
      // fail-fast to prevent any psi modifications that would cause psi/document text mismatch
      if (isDocumentUncommitted(psiFile)) {
        throw new IllegalStateException("Attempt to modify PSI for non-committed Document!");
      }
      CommandProcessor commandProcessor = CommandProcessor.getInstance();
      if (physical && !commandProcessor.isUndoTransparentActionInProgress() && commandProcessor.getCurrentCommand() == null) {
        throw new IncorrectOperationException("Must not change PSI outside command or undo-transparent action. See com.intellij.openapi.command.WriteCommandAction or com.intellij.openapi.command.CommandProcessor");
      }
    }

    VirtualFile vFile = psiFile == null ? null : psiFile.getViewProvider().getVirtualFile();
    if (psiFile != null) {
      ((SmartPointerManagerImpl) SmartPointerManager.getInstance(myProject)).fastenBelts(vFile);
      if (psiFile instanceof PsiFileImpl) {
        ((PsiFileImpl)psiFile).beforeAstChange();
      }
    }

    sendBeforeChildrenChangeEvent(changeScope);
    Document document = psiFile == null || psiFile instanceof DummyHolder ? null :
                        physical ? FileDocumentManager.getInstance().getDocument(vFile) :
                        FileDocumentManager.getInstance().getCachedDocument(vFile);
    if (document != null) {
      synchronizer.startTransaction(myProject, document, psiFile);
    }
    return document;
  }

  private boolean isDocumentUncommitted(@Nullable PsiFile file) {
    if (file == null) return false;

    PsiDocumentManager manager = PsiDocumentManager.getInstance(myProject);
    Document cachedDocument = manager.getCachedDocument(file);
    return cachedDocument != null && manager.isUncommited(cachedDocument);
  }

  private static @Nullable PsiFile getContainingFileByTree(final @NotNull PsiElement changeScope) {
    // there could be pseudo physical trees (JSPX/JSP/etc.) which must not translate
    // any changes to document and not to fire any PSI events
    final PsiFile psiFile;
    final ASTNode node = changeScope.getNode();
    if (node == null) {
      psiFile = changeScope.getContainingFile();
    }
    else {
      final FileASTNode fileElement = TreeUtil.getFileElement(node);
      // assert fileElement != null : "Can't find file element for node: " + node;
      // Hack. the containing tree can be invalidated if updating supplementary trees like HTML in JSP.
      if (fileElement == null) return null;

      psiFile = (PsiFile)fileElement.getPsi();
    }
    return psiFile.getNode() != null ? psiFile : null;
  }

  private static volatile boolean allowPsiModification = true;
  public static <T extends Throwable> void guardPsiModificationsIn(@NotNull ThrowableRunnable<T> runnable) throws T {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    boolean old = allowPsiModification;
    try {
      allowPsiModification = false;
      runnable.run();
    }
    finally {
      allowPsiModification = old;
    }
  }

  public static boolean isAllowPsiModification() {
    return allowPsiModification;
  }

  private void sendBeforeChildrenChangeEvent(@NotNull PsiElement scope) {
    if (!shouldFirePhysicalPsiEvents(scope)) {
      getPsiManager().beforeChange(false);
      return;
    }
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(getPsiManager());
    event.setParent(scope);
    PsiFile containingFile = scope.getContainingFile();
    if (containingFile != null) {
      event.setFile(containingFile);
    }
    TextRange range = scope.getTextRange();
    event.setOffset(range == null ? 0 : range.getStartOffset());
    event.setOldLength(scope.getTextLength());
    // the "generic" event is being sent on every PSI change. It does not carry any specific info except the fact that "something has changed"
    event.setGenericChange(true);
    getPsiManager().beforeChildrenChange(event);
  }

  @ApiStatus.Internal
  public static boolean shouldFirePhysicalPsiEvents(@NotNull PsiElement scope) {
    // injections are physical even in non-physical PSI :(
    return scope.isPhysical();
  }

  private void sendAfterChildrenChangedEvent(@NotNull PsiFile scope, int oldLength) {
    if (!shouldFirePhysicalPsiEvents(scope)) {
      getPsiManager().afterChange(false);
      return;
    }
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(getPsiManager());
    event.setParent(scope);
    event.setFile(scope);
    event.setOffset(0);
    event.setOldLength(oldLength);
    event.setGenericChange(true);
    getPsiManager().childrenChanged(event);
  }

  private @NotNull PsiManagerImpl getPsiManager() {
    return (PsiManagerImpl)PsiManager.getInstance(myProject);
  }
}
