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
package com.intellij.pom.core.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.PomTransaction;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLock;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiToDocumentSynchronizer;
import com.intellij.psi.impl.source.text.BlockSupportImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
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
  private final Collection<PomModelListener> myListeners = new ArrayList<PomModelListener>();

  public PomModelImpl(Project project) {
    myProject = project;
  }

  public <T extends PomModelAspect> T getModelAspect(Class<T> aClass) {
    //noinspection unchecked
    return (T)myAspects.get(aClass);
  }

  public void registerAspect(Class<? extends PomModelAspect> aClass, PomModelAspect aspect, Set<PomModelAspect> dependencies) {
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

  public void addModelListener(PomModelListener listener) {
    synchronized(myListeners){
      myListeners.add(listener);
      myListenersArray = null;
    }
  }

  public void addModelListener(final PomModelListener listener, Disposable parentDisposable) {
    addModelListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeModelListener(listener);
      }
    });
  }

  public void removeModelListener(PomModelListener listener) {
    synchronized(myListeners){
      myListeners.remove(listener);
      myListenersArray = null;
    }
  }

  private final Stack<Pair<PomModelAspect, PomTransaction>> myBlockedAspects = new Stack<Pair<PomModelAspect, PomTransaction>>();

  public void runTransaction(PomTransaction transaction) throws IncorrectOperationException{
    List<Throwable> throwables = new ArrayList<Throwable>(0);
    synchronized(PsiLock.LOCK){
      final PomModelAspect aspect = transaction.getTransactionAspect();
      startTransaction(transaction);
      try{

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
        final PomModelListener[] listeners = getListeners();
        for (final PomModelListener listener : listeners) {
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
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    final PsiDocumentManagerImpl manager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject);
    final PsiToDocumentSynchronizer synchronizer = manager.getSynchronizer();
    Document document = null;
    final PsiFile containingFileByTree = getContainingFileByTree(transaction.getChangeScope());
    if (containingFileByTree != null) {
      document = manager.getCachedDocument(containingFileByTree);
    }
    if (document != null) {
      synchronizer.commitTransaction(document);
    }
    if(progressIndicator != null) progressIndicator.finishNonCancelableSection();
  }

  private void startTransaction(final PomTransaction transaction) {
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if(progressIndicator != null) progressIndicator.startNonCancelableSection();
    final PsiDocumentManagerImpl manager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject);
    final PsiToDocumentSynchronizer synchronizer = manager.getSynchronizer();
    final PsiElement changeScope = transaction.getChangeScope();
    BlockSupportImpl.sendBeforeChildrenChangeEvent(transaction.getChangeScope());
    LOG.assertTrue(changeScope != null);
    final PsiFile containingFileByTree = getContainingFileByTree(changeScope);

    Document document = null;
    if(containingFileByTree != null) {
      document = manager.getCachedDocument(containingFileByTree);
    }
    if(document != null) {
      synchronizer.startTransaction(myProject, document, transaction.getChangeScope());
    }
  }

  @Nullable
  private static PsiFile getContainingFileByTree(@NotNull final PsiElement changeScope) {
    // there could be pseudo phisical trees (JSPX/JSP/etc.) which must not translate
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

  private PomModelListener[] myListenersArray = null;
  private PomModelListener[] getListeners(){
    if(myListenersArray != null) return myListenersArray;
    return myListenersArray = myListeners.toArray(new PomModelListener[myListeners.size()]);
  }
  // Project component

}
