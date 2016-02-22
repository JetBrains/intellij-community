/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.psi.impl;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.PrioritizedInternalDocumentListener;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorDocumentPriorities;
import com.intellij.openapi.editor.impl.FrozenDocument;
import com.intellij.openapi.editor.impl.event.RetargetRangeMarkers;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.*;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

public abstract class PsiDocumentManagerBase extends PsiDocumentManager implements DocumentListener, ProjectComponent {
  static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiDocumentManagerImpl");
  private static final Key<Document> HARD_REF_TO_DOCUMENT = Key.create("HARD_REFERENCE_TO_DOCUMENT");
  private final Key<PsiFile> HARD_REF_TO_PSI = Key.create("HARD_REFERENCE_TO_PSI"); // has to be different for each project to avoid mixups
  private static final Key<List<Runnable>> ACTION_AFTER_COMMIT = Key.create("ACTION_AFTER_COMMIT");

  protected final Project myProject;
  private final PsiManager myPsiManager;
  private final DocumentCommitProcessor myDocumentCommitProcessor;
  protected final Set<Document> myUncommittedDocuments = ContainerUtil.newConcurrentSet();
  private final Map<Document, UncommittedInfo> myUncommittedInfos = ContainerUtil.newConcurrentMap();
  protected boolean myStopTrackingDocuments;
  private boolean myPerformBackgroundCommit = true;

  private volatile boolean myIsCommitInProgress;
  private final PsiToDocumentSynchronizer mySynchronizer;

  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  protected PsiDocumentManagerBase(@NotNull final Project project,
                                   @NotNull PsiManager psiManager,
                                   @NotNull MessageBus bus,
                                   @NonNls @NotNull final DocumentCommitProcessor documentCommitProcessor) {
    myProject = project;
    myPsiManager = psiManager;
    myDocumentCommitProcessor = documentCommitProcessor;
    mySynchronizer = new PsiToDocumentSynchronizer(this, bus);
    myPsiManager.addPsiTreeChangeListener(mySynchronizer);
    bus.connect().subscribe(PsiDocumentTransactionListener.TOPIC, new PsiDocumentTransactionListener() {
      @Override
      public void transactionStarted(@NotNull Document document, @NotNull PsiFile file) {
        myUncommittedDocuments.remove(document);
      }

      @Override
      public void transactionCompleted(@NotNull Document document, @NotNull PsiFile file) {
      }
    });
  }

  @Override
  @Nullable
  public PsiFile getPsiFile(@NotNull Document document) {
    final PsiFile userData = document.getUserData(HARD_REF_TO_PSI);
    if (userData != null) return userData;

    PsiFile psiFile = getCachedPsiFile(document);
    if (psiFile != null) return psiFile;

    final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null || !virtualFile.isValid()) return null;

    psiFile = getPsiFile(virtualFile);
    if (psiFile == null) return null;

    fireFileCreated(document, psiFile);

    return psiFile;
  }

  @Deprecated
  // todo remove when Database Navigator plugin doesn't need that anymore
  // todo to be removed in idea 17
  public static void cachePsi(@NotNull Document document, @Nullable PsiFile file) {
    LOG.warn("Unsupported method");
  }

  public void associatePsi(@NotNull Document document, @Nullable PsiFile file) {
    document.putUserData(HARD_REF_TO_PSI, file);
  }

  @Override
  public PsiFile getCachedPsiFile(@NotNull Document document) {
    final PsiFile userData = document.getUserData(HARD_REF_TO_PSI);
    if (userData != null) return userData;

    final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null || !virtualFile.isValid()) return null;
    return getCachedPsiFile(virtualFile);
  }

  @Nullable
  FileViewProvider getCachedViewProvider(@NotNull Document document) {
    final VirtualFile virtualFile = getVirtualFile(document);
    if (virtualFile == null) return null;
    return getCachedViewProvider(virtualFile);
  }

  private FileViewProvider getCachedViewProvider(@NotNull VirtualFile virtualFile) {
    return ((PsiManagerEx)myPsiManager).getFileManager().findCachedViewProvider(virtualFile);
  }

  private static VirtualFile getVirtualFile(@NotNull Document document) {
    final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null || !virtualFile.isValid()) return null;
    return virtualFile;
  }

  @Nullable
  PsiFile getCachedPsiFile(@NotNull VirtualFile virtualFile) {
    return ((PsiManagerEx)myPsiManager).getFileManager().getCachedPsiFile(virtualFile);
  }

  @Nullable
  private PsiFile getPsiFile(@NotNull VirtualFile virtualFile) {
    return ((PsiManagerEx)myPsiManager).getFileManager().findFile(virtualFile);
  }

  @Override
  public Document getDocument(@NotNull PsiFile file) {
    if (file instanceof PsiBinaryFile) return null;

    Document document = getCachedDocument(file);
    if (document != null) {
      if (!file.getViewProvider().isPhysical() && document.getUserData(HARD_REF_TO_PSI) == null) {
        PsiUtilCore.ensureValid(file);
        associatePsi(document, file);
      }
      return document;
    }

    FileViewProvider viewProvider = file.getViewProvider();
    if (!viewProvider.isEventSystemEnabled()) return null;

    document = FileDocumentManager.getInstance().getDocument(viewProvider.getVirtualFile());
    if (document != null) {
      if (document.getTextLength() != file.getTextLength()) {
        String message = "Document/PSI mismatch: " + file + " (" + file.getClass() + "); physical=" + viewProvider.isPhysical();
        if (document.getTextLength() + file.getTextLength() < 8096) {
          message += "\n=== document ===\n" + document.getText() + "\n=== PSI ===\n" + file.getText();
        }
        throw new AssertionError(message);
      }

      if (!viewProvider.isPhysical()) {
        PsiUtilCore.ensureValid(file);
        associatePsi(document, file);
        file.putUserData(HARD_REF_TO_DOCUMENT, document);
      }
    }

    return document;
  }

  @Override
  public Document getCachedDocument(@NotNull PsiFile file) {
    if (!file.isPhysical()) return null;
    VirtualFile vFile = file.getViewProvider().getVirtualFile();
    return FileDocumentManager.getInstance().getCachedDocument(vFile);
  }

  @Override
  public void commitAllDocuments() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myUncommittedDocuments.isEmpty()) return;

    final Document[] documents = getUncommittedDocuments();
    for (Document document : documents) {
      commitDocument(document);
    }

    LOG.assertTrue(!hasUncommitedDocuments(), myUncommittedDocuments);
  }

  @Override
  public void performForCommittedDocument(@NotNull final Document doc, @NotNull final Runnable action) {
    final Document document = doc instanceof DocumentWindow ? ((DocumentWindow)doc).getDelegate() : doc;
    if (isCommitted(document)) {
      action.run();
    }
    else {
      addRunOnCommit(document, action);
    }
  }

  private final Map<Object, Runnable> actionsWhenAllDocumentsAreCommitted = new LinkedHashMap<Object, Runnable>(); //accessed from EDT only
  private static final Object PERFORM_ALWAYS_KEY = new Object() {
    @Override
    @NonNls
    public String toString() {
      return "PERFORM_ALWAYS";
    }
  };

  /**
   * Cancel previously registered action and schedules (new) action to be executed when all documents are committed.
   *
   * @param key    the (unique) id of the action.
   * @param action The action to be executed after automatic commit.
   *               This action will overwrite any action which was registered under this key earlier.
   *               The action will be executed in EDT.
   * @return true if action has been run immediately, or false if action was scheduled for execution later.
   */
  public boolean cancelAndRunWhenAllCommitted(@NonNls @NotNull Object key, @NotNull final Runnable action) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myProject.isDisposed()) {
      action.run();
      return true;
    }
    if (myUncommittedDocuments.isEmpty()) {
      if (!ApplicationManager.getApplication().hasWriteAction(CommitToPsiFileAction.class)) {
        // in case of fireWriteActionFinished() we didn't execute 'actionsWhenAllDocumentsAreCommitted' yet
        assert actionsWhenAllDocumentsAreCommitted.isEmpty() : actionsWhenAllDocumentsAreCommitted;
      }
      action.run();
      return true;
    }

    checkWeAreOutsideAfterCommitHandler();

    actionsWhenAllDocumentsAreCommitted.put(key, action);
    return false;
  }

  public static void addRunOnCommit(@NotNull Document document, @NotNull Runnable action) {
    synchronized (ACTION_AFTER_COMMIT) {
      List<Runnable> list = document.getUserData(ACTION_AFTER_COMMIT);
      if (list == null) {
        document.putUserData(ACTION_AFTER_COMMIT, list = new SmartList<Runnable>());
      }
      list.add(action);
    }
  }

  @Override
  public void commitDocument(@NotNull final Document doc) {
    final Document document = doc instanceof DocumentWindow ? ((DocumentWindow)doc).getDelegate() : doc;
    if (!isCommitted(document)) {
      doCommit(document);
    }
  }

  // public for Upsource
  public boolean finishCommit(@NotNull final Document document,
                              @NotNull final List<Processor<Document>> finishProcessors,
                              final boolean synchronously,
                              @NotNull final Object reason) {
    assert !myProject.isDisposed() : "Already disposed";
    final boolean[] ok = {true};
    ApplicationManager.getApplication().runWriteAction(new CommitToPsiFileAction(document, myProject) {
      @Override
      public void run() {
        ok[0] = finishCommitInWriteAction(document, finishProcessors, synchronously);
      }
    });

    if (ok[0]) {
      // otherwise changes maybe not synced to the document yet, and injectors will crash
      if (!mySynchronizer.isDocumentAffectedByTransactions(document)) {
        InjectedLanguageManager.getInstance(myProject).startRunInjectors(document, synchronously);
      }
      // run after commit actions outside write action
      runAfterCommitActions(document);
      if (DebugUtil.DO_EXPENSIVE_CHECKS && !ApplicationInfoImpl.isInPerformanceTest()) {
        checkAllElementsValid(document, reason);
      }
    }
    return ok[0];
  }

  protected boolean finishCommitInWriteAction(@NotNull final Document document,
                                              @NotNull final List<Processor<Document>> finishProcessors,
                                              final boolean synchronously) {
    if (myProject.isDisposed()) return false;
    assert !(document instanceof DocumentWindow);

    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile != null) {
      getSmartPointerManager().fastenBelts(virtualFile);
    }

    myIsCommitInProgress = true;
    boolean success = true;
    try {
      final FileViewProvider viewProvider = getCachedViewProvider(document);
      if (viewProvider != null) {
        for (Processor<Document> finishRunnable : finishProcessors) {
          success = finishRunnable.process(document);
          if (synchronously) {
            assert success : finishRunnable + " in " + finishProcessors;
          }
          if (!success) {
            break;
          }
        }
        if (success) {
          clearUncommittedInfo(document);
          if (virtualFile != null) {
            getSmartPointerManager().updatePointerTargetsAfterReparse(virtualFile);
          }
          viewProvider.contentsSynchronized();
        }
      }
      else {
        handleCommitWithoutPsi(document);
      }
    }
    finally {
      if (success) {
        myUncommittedDocuments.remove(document);
      }
      myIsCommitInProgress = false;
    }

    return success;
  }

  private void checkAllElementsValid(@NotNull Document document, @NotNull final Object reason) {
    final PsiFile psiFile = getCachedPsiFile(document);
    if (psiFile != null) {
      psiFile.accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (!element.isValid()) {
            throw new AssertionError("Commit to '" + psiFile.getVirtualFile() + "' has led to invalid element: " + element + "; Reason: '" + reason + "'");
          }
        }
      });
    }
  }

  private void doCommit(@NotNull final Document document) {
    assert !myIsCommitInProgress : "Do not call commitDocument() from inside PSI change listener";
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        // otherwise there are many clients calling commitAllDocs() on PSI childrenChanged()
        if (getSynchronizer().isDocumentAffectedByTransactions(document)) return;

        myIsCommitInProgress = true;
        try {
          myDocumentCommitProcessor.commitSynchronously(document, myProject);
        }
        finally {
          myIsCommitInProgress = false;
        }
        assert !isInUncommittedSet(document) : "Document :" + document;
      }
    });
  }

  @Override
  public <T> T commitAndRunReadAction(@NotNull final Computable<T> computation) {
    final Ref<T> ref = Ref.create(null);
    commitAndRunReadAction(new Runnable() {
      @Override
      public void run() {
        ref.set(computation.compute());
      }
    });
    return ref.get();
  }

  @Override
  public void reparseFiles(@NotNull Collection<VirtualFile> files, boolean includeOpenFiles) {
    FileContentUtilCore.reparseFiles(files);
  }

  @Override
  public void commitAndRunReadAction(@NotNull final Runnable runnable) {
    final Application application = ApplicationManager.getApplication();
    if (SwingUtilities.isEventDispatchThread()) {
      commitAllDocuments();
      runnable.run();
    }
    else {
      if (ApplicationManager.getApplication().isReadAccessAllowed()) {
        LOG.error("Don't call commitAndRunReadAction inside ReadAction, it will cause a deadlock otherwise. "+Thread.currentThread());
      }

      final Semaphore s1 = new Semaphore();
      final Semaphore s2 = new Semaphore();
      final boolean[] committed = {false};

      application.runReadAction(
        new Runnable() {
          @Override
          public void run() {
            if (myUncommittedDocuments.isEmpty()) {
              runnable.run();
              committed[0] = true;
            }
            else {
              s1.down();
              s2.down();
              final Runnable commitRunnable = new Runnable() {
                @Override
                public void run() {
                  commitAllDocuments();
                  s1.up();
                  s2.waitFor();
                }
              };
              final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
              if (progressIndicator == null) {
                ApplicationManager.getApplication().invokeLater(commitRunnable);
              }
              else {
                ApplicationManager.getApplication().invokeLater(commitRunnable, progressIndicator.getModalityState());
              }
            }
          }
        }
      );

      if (!committed[0]) {
        s1.waitFor();
        application.runReadAction(
          new Runnable() {
            @Override
            public void run() {
              s2.up();
              runnable.run();
            }
          }
        );
      }
    }
  }

  /**
   * Schedules action to be executed when all documents are committed.
   *
   * @return true if action has been run immediately, or false if action was scheduled for execution later.
   */
  @Override
  public boolean performWhenAllCommitted(@NotNull final Runnable action) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    checkWeAreOutsideAfterCommitHandler();

    assert !myProject.isDisposed() : "Already disposed: " + myProject;
    if (myUncommittedDocuments.isEmpty()) {
      action.run();
      return true;
    }
    CompositeRunnable actions = (CompositeRunnable)actionsWhenAllDocumentsAreCommitted.get(PERFORM_ALWAYS_KEY);
    if (actions == null) {
      actions = new CompositeRunnable();
      actionsWhenAllDocumentsAreCommitted.put(PERFORM_ALWAYS_KEY, actions);
    }
    actions.add(action);

    ModalityState current = ModalityState.current();
    if (current != ModalityState.NON_MODAL) {
      // re-add all uncommitted documents into the queue with this new modality
      // because this client obviously expects them to commit even inside modal dialog
      for (Document document : myUncommittedDocuments) {
        myDocumentCommitProcessor.commitAsynchronously(myProject, document,
                                                       "re-added with modality "+current+" because performWhenAllCommitted("+current+") was called", current);
      }
    }
    return false;
  }

  private static class CompositeRunnable extends ArrayList<Runnable> implements Runnable {
    @Override
    public void run() {
      for (Runnable runnable : this) {
        runnable.run();
      }
    }
  }

  private void runAfterCommitActions(@NotNull Document document) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<Runnable> list;
    synchronized (ACTION_AFTER_COMMIT) {
      list = document.getUserData(ACTION_AFTER_COMMIT);
      if (list != null) {
        list = new ArrayList<Runnable>(list);
        document.putUserData(ACTION_AFTER_COMMIT, null);
      }
    }
    if (list != null) {
      for (final Runnable runnable : list) {
        runnable.run();
      }
    }

    if (!hasUncommitedDocuments() && !actionsWhenAllDocumentsAreCommitted.isEmpty()) {
      List<Map.Entry<Object, Runnable>> entries = new ArrayList<Map.Entry<Object, Runnable>>(new LinkedHashMap<Object, Runnable>(actionsWhenAllDocumentsAreCommitted).entrySet());
      weAreInsideAfterCommitHandler();

      try {
        for (Map.Entry<Object, Runnable> entry : entries) {
          Runnable action = entry.getValue();
          try {
            action.run();
          }
          catch (Throwable e) {
            LOG.error("During running " + action, e);
          }
        }
      }
      finally {
        actionsWhenAllDocumentsAreCommitted.clear();
      }
    }
  }

  private void weAreInsideAfterCommitHandler() {
    actionsWhenAllDocumentsAreCommitted.put(PERFORM_ALWAYS_KEY, EmptyRunnable.getInstance()); // to prevent listeners from registering new actions during firing
  }
  private void checkWeAreOutsideAfterCommitHandler() {
    if (actionsWhenAllDocumentsAreCommitted.get(PERFORM_ALWAYS_KEY) == EmptyRunnable.getInstance()) {
      throw new IncorrectOperationException("You must not call performWhenAllCommitted()/cancelAndRunWhenCommitted() from within after-commit handler");
    }
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(@NotNull Listener listener) {
    myListeners.remove(listener);
  }

  @Override
  public boolean isDocumentBlockedByPsi(@NotNull Document doc) {
    return false;
  }

  @Override
  public void doPostponedOperationsAndUnblockDocument(@NotNull Document doc) {
  }

  void fireDocumentCreated(@NotNull Document document, PsiFile file) {
    for (Listener listener : myListeners) {
      listener.documentCreated(document, file);
    }
  }

  private void fireFileCreated(@NotNull Document document, @NotNull PsiFile file) {
    for (Listener listener : myListeners) {
      listener.fileCreated(file, document);
    }
  }

  @Override
  @NotNull
  public CharSequence getLastCommittedText(@NotNull Document document) {
    return getLastCommittedDocument(document).getImmutableCharSequence();
  }

  @Override
  public long getLastCommittedStamp(@NotNull Document document) {
    if (document instanceof DocumentWindow) document = ((DocumentWindow)document).getDelegate();
    return getLastCommittedDocument(document).getModificationStamp();
  }

  @Override
  @Nullable
  public Document getLastCommittedDocument(@NotNull PsiFile file) {
    Document document = getDocument(file);
    return document == null ? null : getLastCommittedDocument(document);
  }

  @NotNull
  public DocumentEx getLastCommittedDocument(@NotNull Document document) {
    if (document instanceof FrozenDocument) return (DocumentEx)document;

    if (document instanceof DocumentWindow) {
      DocumentWindow window = (DocumentWindow)document;
      Document delegate = window.getDelegate();
      if (delegate instanceof FrozenDocument) return (DocumentEx)window;

      if (!window.isValid()) {
        throw new AssertionError("host committed: " + isCommitted(delegate) + ", window=" + window);
      }

      UncommittedInfo info = myUncommittedInfos.get(delegate);
      DocumentWindow answer = info == null ? null : info.myFrozenWindows.get(document);
      if (answer == null) answer = freezeWindow(window);
      if (info != null) answer = ConcurrencyUtil.cacheOrGet(info.myFrozenWindows, window, answer);
      return (DocumentEx)answer;
    }

    assert document instanceof DocumentImpl;
    UncommittedInfo info = myUncommittedInfos.get(document);
    return info != null ? info.myFrozen : ((DocumentImpl)document).freeze();
  }

  @NotNull
  protected DocumentWindow freezeWindow(@NotNull DocumentWindow document) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public List<DocumentEvent> getEventsSinceCommit(@NotNull Document document) {
    assert document instanceof DocumentImpl;
    UncommittedInfo info = myUncommittedInfos.get(document);
    if (info != null) {
      return info.myEvents;
    }
    return Collections.emptyList();

  }

  @Override
  @NotNull
  public Document[] getUncommittedDocuments() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Document[] documents = myUncommittedDocuments.toArray(new Document[myUncommittedDocuments.size()]);
    return ArrayUtil.stripTrailingNulls(documents);
  }

  boolean isInUncommittedSet(@NotNull Document document) {
    if (document instanceof DocumentWindow) document = ((DocumentWindow)document).getDelegate();
    return myUncommittedDocuments.contains(document);
  }

  @Override
  public boolean isUncommited(@NotNull Document document) {
    return !isCommitted(document);
  }

  @Override
  public boolean isCommitted(@NotNull Document document) {
    if (document instanceof DocumentWindow) document = ((DocumentWindow)document).getDelegate();
    if (getSynchronizer().isInSynchronization(document)) return true;
    return !((DocumentEx)document).isInEventsHandling() && !isInUncommittedSet(document);
  }

  @Override
  public boolean hasUncommitedDocuments() {
    return !myIsCommitInProgress && !myUncommittedDocuments.isEmpty();
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent event) {
    if (myStopTrackingDocuments || myProject.isDisposed()) return;

    final Document document = event.getDocument();
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    boolean isRelevant = virtualFile != null && isRelevant(virtualFile);

    if (document instanceof DocumentImpl && !myUncommittedInfos.containsKey(document)) {
      myUncommittedInfos.put(document, new UncommittedInfo((DocumentImpl)document));
    }

    final FileViewProvider viewProvider = getCachedViewProvider(document);
    boolean inMyProject = viewProvider != null && viewProvider.getManager() == myPsiManager;
    if (!isRelevant || !inMyProject) {
      return;
    }

    final List<PsiFile> files = viewProvider.getAllFiles();
    PsiFile psiCause = null;
    for (PsiFile file : files) {
      if (file == null) {
        throw new AssertionError("View provider "+viewProvider+" ("+viewProvider.getClass()+") returned null in its files array: "+files+" for file "+viewProvider.getVirtualFile());
      }

      if (mySynchronizer.isInsideAtomicChange(file)) {
        psiCause = file;
      }
    }

    if (psiCause == null) {
      beforeDocumentChangeOnUnlockedDocument(viewProvider);
    }

    ((SingleRootFileViewProvider)viewProvider).beforeDocumentChanged(psiCause);
  }

  protected void beforeDocumentChangeOnUnlockedDocument(@NotNull final FileViewProvider viewProvider) {
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    if (myStopTrackingDocuments || myProject.isDisposed()) return;

    final Document document = event.getDocument();
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    boolean isRelevant = virtualFile != null && isRelevant(virtualFile);

    final FileViewProvider viewProvider = getCachedViewProvider(document);
    if (viewProvider == null) {
      handleCommitWithoutPsi(document);
      return;
    }
    boolean inMyProject = viewProvider.getManager() == myPsiManager;
    if (!isRelevant || !inMyProject) {
      clearUncommittedInfo(document);
      return;
    }

    ApplicationManager.getApplication().assertWriteAccessAllowed();
    final List<PsiFile> files = viewProvider.getAllFiles();
    boolean commitNecessary = true;
    for (PsiFile file : files) {

      if (mySynchronizer.isInsideAtomicChange(file)) {
        commitNecessary = false;
        continue;
      }

      assert file instanceof PsiFileImpl || "mock.file".equals(file.getName()) && ApplicationManager.getApplication().isUnitTestMode() :
        event + "; file=" + file + "; allFiles=" + files + "; viewProvider=" + viewProvider;
    }

    boolean forceCommit = ApplicationManager.getApplication().hasWriteAction(ExternalChangeAction.class) &&
                          (SystemProperties.getBooleanProperty("idea.force.commit.on.external.change", false) ||
                           ApplicationManager.getApplication().isHeadlessEnvironment() && !ApplicationManager.getApplication().isUnitTestMode());

    // Consider that it's worth to perform complete re-parse instead of merge if the whole document text is replaced and
    // current document lines number is roughly above 5000. This makes sense in situations when external change is performed
    // for the huge file (that causes the whole document to be reloaded and 'merge' way takes a while to complete).
    if (event.isWholeTextReplaced() && document.getTextLength() > 100000) {
      document.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, Boolean.TRUE);
    }

    if (commitNecessary) {
      assert !(document instanceof DocumentWindow);
      myUncommittedDocuments.add(document);
      if (forceCommit) {
        commitDocument(document);
      }
      else if (!((DocumentEx)document).isInBulkUpdate() && myPerformBackgroundCommit) {
        myDocumentCommitProcessor.commitAsynchronously(myProject, document, event, ApplicationManager.getApplication().getCurrentModalityState());
      }
    }
    else {
      clearUncommittedInfo(document);
    }
  }

  void handleCommitWithoutPsi(@NotNull Document document) {
    final UncommittedInfo prevInfo = clearUncommittedInfo(document);
    if (prevInfo == null) {
      return;
    }

    if (!myProject.isInitialized() || myProject.isDisposed()) {
      return;
    }
    
    myUncommittedDocuments.remove(document);

    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null || !FileIndexFacade.getInstance(myProject).isInContent(virtualFile)) {
      return;
    }

    final PsiFile psiFile = getPsiFile(document);
    if (psiFile == null) {
      return;
    }

    // we can end up outside write action here if the document has forUseInNonAWTThread=true
    ApplicationManager.getApplication().runWriteAction(new ExternalChangeAction() {
      @Override
      public void run() {
        FileViewProvider viewProvider = psiFile.getViewProvider();
        if (viewProvider instanceof SingleRootFileViewProvider) {
          ((SingleRootFileViewProvider)viewProvider).onContentReload();
        } else {
          LOG.error("Invalid view provider: " + viewProvider + " of " + viewProvider.getClass());
        }
      }
    });
  }

  @Nullable
  private UncommittedInfo clearUncommittedInfo(@NotNull Document document) {
    UncommittedInfo info = myUncommittedInfos.remove(document);
    if (info != null) {
      getSmartPointerManager().updatePointers(document, info.myFrozen, info.myEvents);
      info.removeListener();
    }
    return info;
  }

  private SmartPointerManagerImpl getSmartPointerManager() {
    return (SmartPointerManagerImpl)SmartPointerManager.getInstance(myProject);
  }

  private boolean isRelevant(@NotNull VirtualFile virtualFile) {
    return !virtualFile.getFileType().isBinary() && !myProject.isDisposed();
  }

  public static boolean checkConsistency(@NotNull PsiFile psiFile, @NotNull Document document) {
    //todo hack
    if (psiFile.getVirtualFile() == null) return true;

    CharSequence editorText = document.getCharsSequence();
    int documentLength = document.getTextLength();
    if (psiFile.textMatches(editorText)) {
      LOG.assertTrue(psiFile.getTextLength() == documentLength);
      return true;
    }

    char[] fileText = psiFile.textToCharArray();
    @SuppressWarnings("NonConstantStringShouldBeStringBuffer")
    @NonNls String error = "File '" + psiFile.getName() + "' text mismatch after reparse. " +
                           "File length=" + fileText.length + "; Doc length=" + documentLength + "\n";
    int i = 0;
    for (; i < documentLength; i++) {
      if (i >= fileText.length) {
        error += "editorText.length > psiText.length i=" + i + "\n";
        break;
      }
      if (i >= editorText.length()) {
        error += "editorText.length > psiText.length i=" + i + "\n";
        break;
      }
      if (editorText.charAt(i) != fileText[i]) {
        error += "first unequal char i=" + i + "\n";
        break;
      }
    }
    //error += "*********************************************" + "\n";
    //if (i <= 500){
    //  error += "Equal part:" + editorText.subSequence(0, i) + "\n";
    //}
    //else{
    //  error += "Equal part start:\n" + editorText.subSequence(0, 200) + "\n";
    //  error += "................................................" + "\n";
    //  error += "................................................" + "\n";
    //  error += "................................................" + "\n";
    //  error += "Equal part end:\n" + editorText.subSequence(i - 200, i) + "\n";
    //}
    error += "*********************************************" + "\n";
    error += "Editor Text tail:(" + (documentLength - i) + ")\n";// + editorText.subSequence(i, Math.min(i + 300, documentLength)) + "\n";
    error += "*********************************************" + "\n";
    error += "Psi Text tail:(" + (fileText.length - i) + ")\n";
    error += "*********************************************" + "\n";

    if (document instanceof DocumentWindow) {
      error += "doc: '" + document.getText() + "'\n";
      error += "psi: '" + psiFile.getText() + "'\n";
      error += "ast: '" + psiFile.getNode().getText() + "'\n";
      error += psiFile.getLanguage() + "\n";
      PsiElement context = InjectedLanguageManager.getInstance(psiFile.getProject()).getInjectionHost(psiFile);
      if (context != null) {
        error += "context: " + context + "; text: '" + context.getText() + "'\n";
        error += "context file: " + context.getContainingFile() + "\n";
      }
      error += "document window ranges: " + Arrays.asList(((DocumentWindow)document).getHostRanges()) + "\n";
    }
    LOG.error(error);
    //document.replaceString(0, documentLength, psiFile.getText());
    return false;
  }

  @VisibleForTesting
  public void clearUncommittedDocuments() {
    for (UncommittedInfo info : myUncommittedInfos.values()) {
      info.removeListener();
    }
    myUncommittedInfos.clear();
    myUncommittedDocuments.clear();
    mySynchronizer.cleanupForNextTest();
  }

  @TestOnly
  public void disableBackgroundCommit(@NotNull Disposable parentDisposable) {
    assert myPerformBackgroundCommit;
    myPerformBackgroundCommit = false;
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myPerformBackgroundCommit = true;
      }
    });
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    clearUncommittedDocuments();
  }

  @NotNull
  @Override
  public String getComponentName() {
    return getClass().getSimpleName();
  }

  @NotNull
  public PsiToDocumentSynchronizer getSynchronizer() {
    return mySynchronizer;
  }

  private static class UncommittedInfo extends DocumentAdapter implements PrioritizedInternalDocumentListener {
    private final DocumentImpl myOriginal;
    private final FrozenDocument myFrozen;
    private final List<DocumentEvent> myEvents = ContainerUtil.newArrayList();
    private final ConcurrentMap<DocumentWindow, DocumentWindow> myFrozenWindows = ContainerUtil.newConcurrentMap();

    private UncommittedInfo(DocumentImpl original) {
      myOriginal = original;
      myFrozen = original.freeze();
      myOriginal.addDocumentListener(this);
    }

    @Override
    public int getPriority() {
      return EditorDocumentPriorities.RANGE_MARKER;
    }

    @Override
    public void documentChanged(DocumentEvent e) {
      myEvents.add(e);
    }

    @Override
    public void moveTextHappened(int start, int end, int base) {
      myEvents.add(new RetargetRangeMarkers(myOriginal, start, end, base));
    }

    public void removeListener() {
      myOriginal.removeDocumentListener(this);
    }
  }

}
