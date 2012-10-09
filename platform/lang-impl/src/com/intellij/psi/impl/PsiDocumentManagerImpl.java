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

package com.intellij.psi.impl;

import com.intellij.AppTopics;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;

//todo listen & notifyListeners readonly events?
public class PsiDocumentManagerImpl extends PsiDocumentManager implements ProjectComponent, DocumentListener, SettingsSavingComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiDocumentManagerImpl");
  private static final Key<PsiFile> HARD_REF_TO_PSI = new Key<PsiFile>("HARD_REFERENCE_TO_PSI");
  private static final Key<List<Runnable>> ACTION_AFTER_COMMIT = Key.create("ACTION_AFTER_COMMIT");

  private final Project myProject;
  private final PsiManager myPsiManager;
  private final DocumentCommitThread myDocumentCommitThread;
  private final Set<Document> myUncommittedDocuments = Collections.synchronizedSet(new HashSet<Document>());

  private volatile boolean myIsCommitInProgress;
  private final PsiToDocumentSynchronizer mySynchronizer;

  private final List<Listener> myListeners = ContainerUtil.createEmptyCOWList();
  private final SmartPointerManagerImpl mySmartPointerManager;

  public PsiDocumentManagerImpl(@NotNull final Project project,
                                @NotNull PsiManager psiManager,
                                @NotNull SmartPointerManager smartPointerManager,
                                @NotNull EditorFactory editorFactory,
                                @NotNull MessageBus bus,
                                @NonNls @NotNull final DocumentCommitThread documentCommitThread) {
    myProject = project;
    myPsiManager = psiManager;
    myDocumentCommitThread = documentCommitThread;
    mySmartPointerManager = (SmartPointerManagerImpl)smartPointerManager;
    mySynchronizer = new PsiToDocumentSynchronizer(this, bus);
    myPsiManager.addPsiTreeChangeListener(mySynchronizer);
    editorFactory.getEventMulticaster().addDocumentListener(this, myProject);
    bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      @Override
      public void fileContentLoaded(@NotNull final VirtualFile virtualFile, @NotNull Document document) {
        PsiFile psiFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
          @Override
          public PsiFile compute() {
            return getCachedPsiFile(virtualFile);
          }
        });
        fireDocumentCreated(document, psiFile);
      }
    });
    bus.connect().subscribe(DocumentBulkUpdateListener.TOPIC, new DocumentBulkUpdateListener.Adapter() {
      @Override
      public void updateFinished(@NotNull Document doc) {
        documentCommitThread.queueCommit(project, doc, "Bulk update finished");
      }
    });
    ApplicationManager.getApplication().addApplicationListener(new ApplicationAdapter() {
      @Override
      public void beforeWriteActionStart(Object action) {
        documentCommitThread.disable("Write action started: " + action);
      }

      @Override
      public void writeActionFinished(Object action) {
        documentCommitThread.enable("Write action finished: " + action);
      }
    }, myProject);
    documentCommitThread.enable("project open");
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "PsiDocumentManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
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

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      Collection<Project> projects = ProjectLocator.getInstance().getProjectsForFile(virtualFile);
      LOG.assertTrue(projects.isEmpty() || projects.contains(myProject), "Trying to get PSI for an alien project. VirtualFile=" +
                                                                         virtualFile +
                                                                         ";\n myProject=" +
                                                                         myProject +
                                                                         ";\n projects returned: " +
                                                                         projects);
    }

    psiFile = getPsiFile(virtualFile);
    if (psiFile == null) return null;

    fireFileCreated(document, psiFile);

    return psiFile;
  }

  public static void cachePsi(@NotNull Document document, @NotNull PsiFile file) {
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
  public FileViewProvider getCachedViewProvider(@NotNull Document document) {
    final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null || !virtualFile.isValid()) return null;
    return ((PsiManagerEx)myPsiManager).getFileManager().findCachedViewProvider(virtualFile);
  }

  @Nullable
  protected PsiFile getCachedPsiFile(VirtualFile virtualFile) {
    return ((PsiManagerEx)myPsiManager).getFileManager().getCachedPsiFile(virtualFile);
  }

  @Nullable
  protected PsiFile getPsiFile(VirtualFile virtualFile) {
    return ((PsiManagerEx)myPsiManager).getFileManager().findFile(virtualFile);
  }

  @Nullable
  @Override
  public Document getDocument(@NotNull PsiFile file) {
    if (file instanceof PsiBinaryFile) return null;

    Document document = getCachedDocument(file);
    if (document != null) {
      if (!file.getViewProvider().isPhysical() &&
          document.getUserData(HARD_REF_TO_PSI) == null) {
        cachePsi(document, file);
      }
      return document;
    }

    if (!file.getViewProvider().isEventSystemEnabled()) return null;
    document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());

    if (document != null) {
      if (document.getTextLength() != file.getTextLength()) {
        throw new AssertionError("Modified PSI with no document: " + file + "; physical=" + file.getViewProvider().isPhysical());
      }

      if (!file.getViewProvider().isPhysical()) {
        cachePsi(document, file);
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
   * Schedules action to be executed when all documents are committed.
   *
   * @return true if action has been run immediately, or false if action was scheduled for execution later.
   */
  @Override
  public boolean performWhenAllCommitted(@NotNull final Runnable action) {
    ApplicationManager.getApplication().assertIsDispatchThread();
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
    myDocumentCommitThread.log("PDI: added performWhenAllCommitted", null, false, action, myUncommittedDocuments);
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
      action.run();
      assert actionsWhenAllDocumentsAreCommitted.isEmpty() : actionsWhenAllDocumentsAreCommitted;
      return true;
    }
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
      doCommit(document, null);
    }
  }

  boolean finishCommit(@NotNull final Document document,
                       @NotNull final List<Processor<Document>> finishProcessors,
                       final boolean synchronously,
                       @NotNull Object reason) {
    assert !myProject.isDisposed() : "Already disposed";
    final boolean[] ok = {true};
    ApplicationManager.getApplication().runWriteAction(new CommitToPsiFileAction(document, myProject) {
      @Override
      public void run() {
        if (myProject.isDisposed()) return;
        EditorWindow.disposeInvalidEditors();  // in write action
        assert !(document instanceof DocumentWindow);
        myIsCommitInProgress = true;
        boolean success = true;
        try {
          final FileViewProvider viewProvider = getCachedViewProvider(document);
          if (viewProvider != null) {
            for (Processor<Document> finishRunnable : finishProcessors) {
              success = finishRunnable.process(document);
              if (synchronously) {
                assert success;
              }
              if (!success) {
                break;
              }
            }
            viewProvider.contentsSynchronized();
          }
          ok[0] = success;
        }
        finally {
          myDocumentCommitThread.log("in PDI.finishDoc: ", null, synchronously, success, myUncommittedDocuments);
          if (success) {
            myUncommittedDocuments.remove(document);
            myDocumentCommitThread.log("in PDI.finishDoc: removed doc", null, synchronously, success, myUncommittedDocuments);
          }
          myIsCommitInProgress = false;
          myDocumentCommitThread.log("in PDI.finishDoc: exit", null, synchronously, success, myUncommittedDocuments);
        }
      }
    });

    if (ok[0]) {
      // otherwise changes maybe not synced to the document yet, and injectors will crash
      if (!mySynchronizer.isDocumentAffectedByTransactions(document)) {
        ((InjectedLanguageManagerImpl)InjectedLanguageManager.getInstance(myProject)).startRunInjectors(document, synchronously);
      }
      // run after commit actions outside write action
      runAfterCommitActions(document);
      if (DebugUtil.DO_EXPENSIVE_CHECKS) {
        checkAllElementsValid(document, reason);
      }
    }
    return ok[0];
  }

  private void checkAllElementsValid(@NotNull Document document, @NotNull final Object reason) {
    final PsiFile psiFile = getCachedPsiFile(document);
    if (psiFile != null) {
      psiFile.accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (!element.isValid()) {
            LOG.error("Commit to '" + psiFile.getVirtualFile() + "' lead to invalid element: " + element + "; Reason: '" + reason + "'");
          }
        }
      });
    }
  }

  private void doCommit(@NotNull final Document document, final PsiFile excludeFile) {
    assert !myIsCommitInProgress : "Do not call commitDocument() from inside PSI change listener";
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        // otherwise there are many clients calling commitAllDocs() on PSI childrenChanged()
        if (getSynchronizer().isDocumentAffectedByTransactions(document) && excludeFile == null) return;

        myIsCommitInProgress = true;
        try {
          myDocumentCommitThread.commitSynchronously(document, myProject, excludeFile);
        }
        finally {
          myIsCommitInProgress = false;
        }
        assert !myUncommittedDocuments.contains(document) : "Document :" + document;
      }
    });
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
      List<Object> keys = new ArrayList<Object>(actionsWhenAllDocumentsAreCommitted.keySet());
      for (Object key : keys) {
        Runnable action = actionsWhenAllDocumentsAreCommitted.remove(key);
        myDocumentCommitThread.log("Running after commit runnable: ", null, false, key, action);
        action.run();
      }
    }
  }

  public void commitOtherFilesAssociatedWithDocument(final Document document, final PsiFile psiFile) {
    final FileViewProvider viewProvider = getCachedViewProvider(document);
    if (viewProvider != null && viewProvider.getAllFiles().size() > 1) {
      PostprocessReformattingAspect.getInstance(myProject).disablePostprocessFormattingInside(new Runnable() {
        @Override
        public void run() {
          doCommit(document, psiFile);
        }
      });
    }
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
  public void commitAndRunReadAction(@NotNull final Runnable runnable) {
    final Application application = ApplicationManager.getApplication();
    if (SwingUtilities.isEventDispatchThread()) {
      commitAllDocuments();
      runnable.run();
    }
    else {
      LOG.assertTrue(!ApplicationManager.getApplication().isReadAccessAllowed(),
                     "Don't call commitAndRunReadAction inside ReadAction, it will cause a deadlock otherwise.");

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
    final FileViewProvider viewProvider = getCachedViewProvider(doc);
    return viewProvider != null && PostprocessReformattingAspect.getInstance(myProject).isViewProviderLocked(viewProvider);
  }

  @Override
  public void doPostponedOperationsAndUnblockDocument(@NotNull Document doc) {
    if (doc instanceof DocumentWindow) doc = ((DocumentWindow)doc).getDelegate();
    final PostprocessReformattingAspect component = myProject.getComponent(PostprocessReformattingAspect.class);
    final FileViewProvider viewProvider = getCachedViewProvider(doc);
    if (viewProvider != null) component.doPostponedFormatting(viewProvider);
  }

  private void fireDocumentCreated(@NotNull Document document, PsiFile file) {
    for (Listener listener : myListeners) {
      listener.documentCreated(document, file);
    }
  }

  private void fireFileCreated(Document document, PsiFile file) {
    for (Listener listener : myListeners) {
      listener.fileCreated(file, document);
    }
  }

  @Override
  @NotNull
  public Document[] getUncommittedDocuments() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myUncommittedDocuments.toArray(new Document[myUncommittedDocuments.size()]);
  }

  public Collection<Document> getUncommittedDocumentsUnsafe() {
    return myUncommittedDocuments;
  }

  @Override
  public boolean isUncommited(@NotNull Document document) {
    return !isCommitted(document);
  }

  @Override
  public boolean isCommitted(@NotNull Document document) {
    if (getSynchronizer().isInSynchronization(document)) return true;
    return !((DocumentEx)document).isInEventsHandling() && !myUncommittedDocuments.contains(document);
  }

  @Override
  public boolean hasUncommitedDocuments() {
    return !myIsCommitInProgress && !myUncommittedDocuments.isEmpty();
  }

  private final Key<ASTNode> TEMP_TREE_IN_DOCUMENT_KEY = Key.create("TEMP_TREE_IN_DOCUMENT_KEY");

  void clearTreeHardRef(@NotNull Document document) {
    document.putUserData(TEMP_TREE_IN_DOCUMENT_KEY, null);
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    final Document document = event.getDocument();

    final FileViewProvider viewProvider = getCachedViewProvider(document);
    if (viewProvider == null) return;
    if (!isRelevant(viewProvider)) return;

    VirtualFile virtualFile = viewProvider.getVirtualFile();
    if (virtualFile.getFileType().isBinary()) return;

    final List<PsiFile> files = viewProvider.getAllFiles();
    boolean hasLockedBlocks = false;
    for (PsiFile file : files) {
      if (file == null) continue;

      if (file.isPhysical() && mySmartPointerManager != null) { // mock tests
        mySmartPointerManager.fastenBelts(file, event.getOffset(), null);
      }

      final TextBlock textBlock = TextBlock.get(file);
      if (textBlock.isLocked()) {
        hasLockedBlocks = true;
        continue;
      }

      if (file instanceof PsiFileImpl) {
        myIsCommitInProgress = true;
        try {
          PsiFileImpl psiFile = (PsiFileImpl)file;
          // tree should be initialized and be kept until commit
          document.putUserData(TEMP_TREE_IN_DOCUMENT_KEY, psiFile.calcTreeElement());
        }
        finally {
          myIsCommitInProgress = false;
        }
      }
    }

    if (!hasLockedBlocks) {
      PostprocessReformattingAspect.getInstance(myProject).beforeDocumentChanged(viewProvider);
      ((SingleRootFileViewProvider)viewProvider).beforeDocumentChanged();
    }
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    final Document document = event.getDocument();
    final FileViewProvider viewProvider = getCachedViewProvider(document);
    if (viewProvider == null) return;
    if (!isRelevant(viewProvider)) return;

    ApplicationManager.getApplication().assertWriteAccessAllowed();
    final List<PsiFile> files = viewProvider.getAllFiles();
    boolean commitNecessary = false;
    for (PsiFile file : files) {
      if (file == null || file instanceof PsiFileImpl && ((PsiFileImpl)file).getTreeElement() == null) continue;
      if (mySmartPointerManager != null) { // mock tests
        mySmartPointerManager.unfastenBelts(file, event.getOffset());
      }
      final TextBlock textBlock = TextBlock.get(file);
      if (textBlock.isLocked()) continue;

      textBlock.documentChanged(event);
      assert file instanceof PsiFileImpl || "mock.file".equals(file.getName()) && ApplicationManager.getApplication().isUnitTestMode() :
        event +
        "; file=" +
        file +
        "; allFiles=" +
        files +
        "; viewProvider=" +
        viewProvider;

      commitNecessary = true;
    }
    boolean fromRefresh = ApplicationManager.getApplication().hasWriteAction(ExternalChangeAction.class);

    if (commitNecessary) {
      myUncommittedDocuments.add(document);
      if (!fromRefresh && !((DocumentEx)document).isInBulkUpdate()) {
        myDocumentCommitThread.queueCommit(myProject, document, event);
      }
    }

    // Consider that it's worth to perform complete re-parse instead of merge if the whole document text is replaced and
    // current document lines number is roughly above 5000. This makes sense in situations when external change is performed
    // for the huge file (that causes the whole document to be reloaded and 'merge' way takes a while to complete).
    if (event.isWholeTextReplaced() && document.getTextLength() > 100000) {
      document.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, Boolean.TRUE);
    }

    if (commitNecessary && fromRefresh) {
      commitDocument(document);
    }
    // avoid documents piling up during batch processing
    if (FileDocumentManagerImpl.areTooManyDocumentsInTheQueue(myUncommittedDocuments)) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        try {
          LOG.error("Too many uncommitted documents for " + myProject + ":\n" + myUncommittedDocuments);
        }
        finally {
          clearUncommitedDocuments();
        }
      }
      commitAllDocuments();
    }
  }

  private boolean isRelevant(@NotNull FileViewProvider viewProvider) {
    VirtualFile virtualFile = viewProvider.getVirtualFile();
    return !virtualFile.getFileType().isBinary() &&
           viewProvider.getManager() == myPsiManager &&
           !myPsiManager.getProject().isDisposed();
  }

  public static boolean checkConsistency(PsiFile psiFile, Document document) {
    //todo hack
    if (psiFile.getVirtualFile() == null) return true;

    CharSequence editorText = document.getCharsSequence();
    int documentLength = document.getTextLength();
    if (psiFile.textMatches(editorText)) {
      LOG.assertTrue(psiFile.getTextLength() == documentLength);
      return true;
    }

    char[] fileText = psiFile.textToCharArray();
    @SuppressWarnings({"NonConstantStringShouldBeStringBuffer"})
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
    error += "Psi Text tail:(" + (fileText.length - i) + ")\n";// + new String(fileText, i, Math.min(i + 300, fileText.length) - i) + "\n";
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

  @TestOnly
  public void clearUncommitedDocuments() {
    myUncommittedDocuments.clear();
    mySynchronizer.cleanupForNextTest();
    myDocumentCommitThread.clearQueue();
  }

  public PsiToDocumentSynchronizer getSynchronizer() {
    return mySynchronizer;
  }


  @Override
  public void save() {
    // Ensure all documents are committed on save so file content dependent indices, that use PSI to build have consistent content.
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        commitAllDocuments();
      }
    });
  }

  @NonNls
  @Override
  public String toString() {
    return super.toString() + " for the project " + myProject + ".";
  }
}
