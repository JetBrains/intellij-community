/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.pom.core.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.PomTransaction;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.TreeAspectEvent;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.text.BlockSupportImpl;
import com.intellij.psi.impl.source.text.DiffLog;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.lang.CompoundRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PomModelImpl extends UserDataHolderBase implements PomModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.pom.core.impl.PomModelImpl");
  private final Project myProject;
  private final Map<Class<? extends PomModelAspect>, PomModelAspect> myAspects = new HashMap<Class<? extends PomModelAspect>, PomModelAspect>();
  private final Map<PomModelAspect, List<PomModelAspect>> myIncidence = new HashMap<PomModelAspect, List<PomModelAspect>>();
  private final Map<PomModelAspect, List<PomModelAspect>> myInvertedIncidence = new HashMap<PomModelAspect, List<PomModelAspect>>();
  private final Collection<PomModelListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public PomModelImpl(Project project) {
    myProject = project;
  }

  @Override
  public <T extends PomModelAspect> T getModelAspect(@NotNull Class<T> aClass) {
    //noinspection unchecked
    return (T)myAspects.get(aClass);
  }

  @Override
  public void registerAspect(@NotNull Class<? extends PomModelAspect> aClass, @NotNull PomModelAspect aspect, @NotNull Set<PomModelAspect> dependencies) {
    myAspects.put(aClass, aspect);
    final Iterator<PomModelAspect> iterator = dependencies.iterator();
    final List<PomModelAspect> deps = new ArrayList<PomModelAspect>();
    // todo: reorder dependencies
    while (iterator.hasNext()) {
      final PomModelAspect depend = iterator.next();
      deps.addAll(getAllDependencies(depend));
    }
    deps.add(aspect); // add self to block same aspect transactions from event processing and update
    for (final PomModelAspect pomModelAspect : deps) {
      final List<PomModelAspect> pomModelAspects = myInvertedIncidence.get(pomModelAspect);
      if (pomModelAspects != null) {
        pomModelAspects.add(aspect);
      }
      else {
        myInvertedIncidence.put(pomModelAspect, new ArrayList<PomModelAspect>(Collections.singletonList(aspect)));
      }
    }
    myIncidence.put(aspect, deps);
  }

  //private final Pair<PomModelAspect, PomModelAspect> myHolderPair = new Pair<PomModelAspect, PomModelAspect>(null, null);
  private List<PomModelAspect> getAllDependencies(PomModelAspect aspect){
    List<PomModelAspect> pomModelAspects = myIncidence.get(aspect);
    return pomModelAspects != null ? pomModelAspects : Collections.<PomModelAspect>emptyList();
  }

  private List<PomModelAspect> getAllDependants(PomModelAspect aspect){
    List<PomModelAspect> pomModelAspects = myInvertedIncidence.get(aspect);
    return pomModelAspects != null ? pomModelAspects : Collections.<PomModelAspect>emptyList();
  }

  @Override
  public void addModelListener(@NotNull PomModelListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void addModelListener(@NotNull final PomModelListener listener, @NotNull Disposable parentDisposable) {
    addModelListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removeModelListener(listener);
      }
    });
  }

  @Override
  public void removeModelListener(@NotNull PomModelListener listener) {
    myListeners.remove(listener);
  }

  private final Stack<Pair<PomModelAspect, PomTransaction>> myBlockedAspects = new Stack<Pair<PomModelAspect, PomTransaction>>();

  @Override
  public void runTransaction(@NotNull PomTransaction transaction) throws IncorrectOperationException{
    if (!allowPsiModification) {
      throw new IncorrectOperationException("Must not modify PSI inside save listener");
    }
    List<Throwable> throwables = new ArrayList<Throwable>(0);
    synchronized(PsiLock.LOCK){
      final PomModelAspect aspect = transaction.getTransactionAspect();
      startTransaction(transaction);
      try{
        DebugUtil.startPsiModification(null);
        myBlockedAspects.push(new Pair<PomModelAspect, PomTransaction>(aspect, transaction));

        final PomModelEvent event;
        try{
          transaction.run();
          event = transaction.getAccumulatedEvent();
        }
        catch(Exception e){
          LOG.error(e);
          return;
        }
        finally{
          myBlockedAspects.pop();
        }
        final Pair<PomModelAspect,PomTransaction> block = getBlockingTransaction(aspect, transaction);
        if(block != null){
          final PomModelEvent currentEvent = block.getSecond().getAccumulatedEvent();
          currentEvent.merge(event);
          return;
        }

        { // update
          final Set<PomModelAspect> changedAspects = event.getChangedAspects();
          final Collection<PomModelAspect> dependants = new LinkedHashSet<PomModelAspect>();
          for (final PomModelAspect pomModelAspect : changedAspects) {
            dependants.addAll(getAllDependants(pomModelAspect));
          }
          for (final PomModelAspect modelAspect : dependants) {
            if (!changedAspects.contains(modelAspect)) {
              modelAspect.update(event);
            }
          }
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
      catch (Throwable t) {
        throwables.add(t);
      }
      finally {
        try {
          commitTransaction(transaction);
        }
        catch (Throwable t) {
          throwables.add(t);
        }
        finally {
          DebugUtil.finishPsiModification();
        }
      }
    }

    if (!throwables.isEmpty()) CompoundRuntimeException.doThrow(throwables);
  }

  @Nullable
  private Pair<PomModelAspect,PomTransaction> getBlockingTransaction(final PomModelAspect aspect, PomTransaction transaction) {
    final List<PomModelAspect> allDependants = getAllDependants(aspect);
    for (final PomModelAspect pomModelAspect : allDependants) {
      final ListIterator<Pair<PomModelAspect, PomTransaction>> blocksIterator = myBlockedAspects.listIterator(myBlockedAspects.size());
      while (blocksIterator.hasPrevious()) {
        final Pair<PomModelAspect, PomTransaction> pair = blocksIterator.previous();
        if (pomModelAspect == pair.getFirst() && // aspect dependance
            PsiTreeUtil.isAncestor(pair.getSecond().getChangeScope(), transaction.getChangeScope(), false) &&
            // target scope contain current
            getContainingFileByTree(pair.getSecond().getChangeScope()) != null  // target scope physical
          ) {
          return pair;
        }
      }
    }
    return null;
  }

  private void commitTransaction(final PomTransaction transaction) {
    final ProgressIndicator progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    final PsiDocumentManagerBase manager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject);
    final PsiToDocumentSynchronizer synchronizer = manager.getSynchronizer();
    final PsiFile containingFileByTree = getContainingFileByTree(transaction.getChangeScope());
    Document document = containingFileByTree != null ? manager.getCachedDocument(containingFileByTree) : null;
    if (document != null) {
      final int oldLength = containingFileByTree.getTextLength();
      boolean success = synchronizer.commitTransaction(document);
      if (success) {
        BlockSupportImpl.sendAfterChildrenChangedEvent((PsiManagerImpl)PsiManager.getInstance(myProject), (PsiFileImpl)containingFileByTree, oldLength, true);
      }
    }
    if (containingFileByTree != null) {
      boolean isFromCommit = ApplicationManager.getApplication().isDispatchThread() &&
                             ApplicationManager.getApplication().hasWriteAction(CommitToPsiFileAction.class);
      if (!isFromCommit && !synchronizer.isIgnorePsiEvents()) {
        reparseParallelTrees(containingFileByTree);
      }
    }

    if (progressIndicator != null) progressIndicator.finishNonCancelableSection();
  }

  private void reparseParallelTrees(PsiFile changedFile) {
    List<PsiFile> allFiles = changedFile.getViewProvider().getAllFiles();
    if (allFiles.size() <= 1) {
      return;
    }

    String newText = changedFile.getNode().getText();
    for (final PsiFile file : allFiles) {
      if (file != changedFile) {
        FileElement fileElement = ((PsiFileImpl)file).getTreeElement();
        if (fileElement != null) {
          String oldText = fileElement.getText();
          reparseFile(file, newText, oldText);
        }
      }
    }
  }

  private void reparseFile(final PsiFile file, String newText, String oldText) {
    if (oldText.equals(newText)) return;

    PsiToDocumentSynchronizer synchronizer =((PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject)).getSynchronizer();
    int changeStart = StringUtil.commonPrefixLength(oldText, newText);
    int changeEnd = oldText.length() - StringUtil.commonSuffixLength(oldText, newText);
    TextRange changedPsiRange = DocumentCommitProcessor.getChangedPsiRange(file, changeStart, changeEnd, newText.length());
    final DiffLog log = BlockSupport.getInstance(myProject).reparseRange(file, changedPsiRange, newText, new EmptyProgressIndicator());
    synchronizer.setIgnorePsiEvents(true);
    try {
      CodeStyleManager.getInstance(file.getProject()).performActionWithFormatterDisabled(new Runnable() {
        @Override
        public void run() {
          runTransaction(new PomTransactionBase(file, getModelAspect(TreeAspect.class)) {
            @Nullable
            @Override
            public PomModelEvent runInner() throws IncorrectOperationException {
              return new TreeAspectEvent(PomModelImpl.this, log.performActualPsiChange(file));
            }
          });
        }
      });
    }
    finally {
      synchronizer.setIgnorePsiEvents(false);
    }
  }

  private void startTransaction(final PomTransaction transaction) {
    final ProgressIndicator progressIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if(progressIndicator != null) progressIndicator.startNonCancelableSection();
    final PsiDocumentManagerBase manager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(myProject);
    final PsiToDocumentSynchronizer synchronizer = manager.getSynchronizer();
    final PsiElement changeScope = transaction.getChangeScope();
    BlockSupportImpl.sendBeforeChildrenChangeEvent((PsiManagerImpl)PsiManager.getInstance(myProject), transaction.getChangeScope(), true);
    LOG.assertTrue(changeScope != null);
    final PsiFile containingFileByTree = getContainingFileByTree(changeScope);

    Document document = containingFileByTree == null ? null : manager.getCachedDocument(containingFileByTree);
    if(document != null) {
      synchronizer.startTransaction(myProject, document, transaction.getChangeScope());
    }
  }

  @Nullable
  private static PsiFile getContainingFileByTree(@NotNull final PsiElement changeScope) {
    // there could be pseudo physical trees (JSPX/JSP/etc.) which must not translate
    // any changes to document and not to fire any PSI events
    final PsiFile psiFile;
    final ASTNode node = changeScope.getNode();
    if (node == null) {
      psiFile = changeScope.getContainingFile();
    }
    else {
      final FileElement fileElement = TreeUtil.getFileElement((TreeElement)node);
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
}
