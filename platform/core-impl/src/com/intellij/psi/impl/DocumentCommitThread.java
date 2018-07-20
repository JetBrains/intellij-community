// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.lang.FileASTNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSetQueue;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.ide.PooledThreadExecutor;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DocumentCommitThread implements Runnable, Disposable, DocumentCommitProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.DocumentCommitThread");
  private static final String SYNC_COMMIT_REASON = "Sync commit";

  private final ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Document Committing Pool", PooledThreadExecutor.INSTANCE, 1, this);
  private final Object lock = new Object();
  private final HashSetQueue<CommitTask> documentsToCommit = new HashSetQueue<>();      // guarded by lock
  private final HashSetQueue<CommitTask> documentsToApplyInEDT = new HashSetQueue<>();  // guarded by lock
  private final ApplicationEx myApplication;
  private volatile boolean isDisposed;
  private CommitTask currentTask; // guarded by lock
  private boolean myEnabled; // true if we can do commits. set to false temporarily during the write action.  guarded by lock

  static DocumentCommitThread getInstance() {
    return (DocumentCommitThread)ServiceManager.getService(DocumentCommitProcessor.class);
  }
  DocumentCommitThread(final ApplicationEx application) {
    myApplication = application;
    // install listener in EDT to avoid missing events in case we are inside write action right now
    application.invokeLater(() -> {
      if (application.isDisposed()) return;
      assert !application.isWriteAccessAllowed() || application.isUnitTestMode(); // crazy stuff happens in tests, e.g. UIUtil.dispatchInvocationEvents() inside write action
      application.addApplicationListener(new ApplicationAdapter() {
        @Override
        public void beforeWriteActionStart(@NotNull Object action) {
          disable("Write action started: " + action);
        }

        @Override
        public void afterWriteActionFinished(@NotNull Object action) {
          // crazy things happen when running tests, like starting write action in one thread but firing its end in the other
          enable("Write action finished: " + action);
        }
      }, this);

      enable("Listener installed, started");
    });
  }

  @Override
  public void dispose() {
    isDisposed = true;
    synchronized (lock) {
      documentsToCommit.clear();
    }
    cancel("Stop thread", false);
  }

  private void disable(@NonNls @NotNull Object reason) {
    // write action has just started, all commits are useless
    synchronized (lock) {
      cancel(reason, true);
      myEnabled = false;
    }
    log(null, "disabled", null, reason);
  }

  private void enable(@NonNls @NotNull Object reason) {
    synchronized (lock) {
      myEnabled = true;
      wakeUpQueue();
    }
    log(null, "enabled", null, reason);
  }

  // under lock
  private void wakeUpQueue() {
    if (!isDisposed && !documentsToCommit.isEmpty()) {
      executor.execute(this);
    }
  }

  private void cancel(@NonNls @NotNull Object reason, boolean canReQueue) {
    startNewTask(null, reason, canReQueue);
  }

  @Override
  public void commitAsynchronously(@NotNull final Project project,
                                   @NotNull final Document document,
                                   @NonNls @NotNull Object reason,
                                   @Nullable TransactionId context) {
    assert !isDisposed : "already disposed";

    if (!project.isInitialized()) return;
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    PsiFile psiFile = documentManager.getCachedPsiFile(document);
    if (psiFile == null || psiFile instanceof PsiCompiledElement) return;
    doQueue(project, document, getAllFileNodes(psiFile), reason, context, documentManager.getLastCommittedText(document));
  }

  private void doQueue(@NotNull Project project,
                       @NotNull Document document,
                       @NotNull List<Pair<PsiFileImpl, FileASTNode>> oldFileNodes,
                       @NotNull Object reason,
                       @Nullable TransactionId context,
                       @NotNull CharSequence lastCommittedText) {
    synchronized (lock) {
      if (!project.isInitialized()) return;  // check the project is disposed under lock.
      CommitTask newTask = createNewTaskAndCancelSimilar(project, document, oldFileNodes, reason, context, lastCommittedText, false);

      documentsToCommit.offer(newTask);
      log(project, "Queued", newTask, reason);

      wakeUpQueue();
    }
  }

  @NotNull
  private CommitTask createNewTaskAndCancelSimilar(@NotNull Project project,
                                                   @NotNull Document document,
                                                   @NotNull List<Pair<PsiFileImpl, FileASTNode>> oldFileNodes,
                                                   @NotNull Object reason,
                                                   @Nullable TransactionId context,
                                                   @NotNull CharSequence lastCommittedText, boolean canReQueue) {
    synchronized (lock) {
      for (Pair<PsiFileImpl, FileASTNode> pair : oldFileNodes) {
        assert pair.first.getProject() == project;
      }
      CommitTask newTask = new CommitTask(project, document, oldFileNodes, createProgressIndicator(), reason, context, lastCommittedText);
      cancelAndRemoveFromDocsToCommit(newTask, reason, canReQueue);
      cancelAndRemoveCurrentTask(newTask, reason, canReQueue);
      cancelAndRemoveFromDocsToApplyInEDT(newTask, reason, canReQueue);

      return newTask;
    }
  }

  @SuppressWarnings("unused")
  private void log(Project project, @NonNls String msg, @Nullable CommitTask task, @NonNls Object... args) {
    //System.out.println(msg + "; task: "+task + "; args: "+StringUtil.first(Arrays.toString(args), 80, true));
  }


  // cancels all pending commits
  @TestOnly // under lock
  private void cancelAll() {
    String reason = "Cancel all in tests";
    cancel(reason, false);
    for (CommitTask commitTask : documentsToCommit) {
      commitTask.cancel(reason, false);
      log(commitTask.project, "Removed from background queue", commitTask);
    }
    documentsToCommit.clear();
    for (CommitTask commitTask : documentsToApplyInEDT) {
      commitTask.cancel(reason, false);
      log(commitTask.project, "Removed from EDT apply queue (sync commit called)", commitTask);
    }
    documentsToApplyInEDT.clear();
    CommitTask task = currentTask;
    if (task != null) {
      cancelAndRemoveFromDocsToCommit(task, reason, false);
    }
    cancel("Sync commit intervened", false);
    ((BoundedTaskExecutor)executor).clearAndCancelAll();
  }

  @TestOnly
  void clearQueue() {
    synchronized (lock) {
      cancelAll();
      wakeUpQueue();
    }
  }

  private void cancelAndRemoveCurrentTask(@NotNull CommitTask newTask, @NotNull Object reason, boolean canReQueue) {
    CommitTask currentTask = this.currentTask;
    if (newTask.equals(currentTask)) {
      cancelAndRemoveFromDocsToCommit(currentTask, reason, canReQueue);
      cancel(reason, canReQueue);
    }
  }

  private void cancelAndRemoveFromDocsToApplyInEDT(@NotNull CommitTask newTask, @NotNull Object reason, boolean canReQueue) {
    boolean removed = cancelAndRemoveFromQueue(newTask, documentsToApplyInEDT, reason, canReQueue);
    if (removed) {
      log(newTask.project, "Removed from EDT apply queue", newTask);
    }
  }

  private void cancelAndRemoveFromDocsToCommit(@NotNull final CommitTask newTask, @NotNull Object reason, boolean canReQueue) {
    boolean removed = cancelAndRemoveFromQueue(newTask, documentsToCommit, reason, canReQueue);
    if (removed) {
      log(newTask.project, "Removed from background queue", newTask);
    }
  }

  private boolean cancelAndRemoveFromQueue(@NotNull CommitTask newTask, @NotNull HashSetQueue<CommitTask> queue, @NotNull Object reason, boolean canReQueue) {
    CommitTask queuedTask = queue.find(newTask);
    if (queuedTask != null) {
      assert queuedTask != newTask;
      queuedTask.cancel(reason, canReQueue);
    }
    return queue.remove(newTask);
  }

  @Override
  public void run() {
    while (!isDisposed) {
      try {
        if (!pollQueue()) break;
      }
      catch(Throwable e) {
        LOG.error(e);
      }
    }
  }

  // returns true if queue changed
  private boolean pollQueue() {
    assert !myApplication.isDispatchThread() : Thread.currentThread();
    CommitTask task;
    synchronized (lock) {
      if (!myEnabled || (task = documentsToCommit.poll()) == null) {
        return false;
      }

      Document document = task.getDocument();
      Project project = task.project;

      if (project.isDisposed() || !((PsiDocumentManagerBase)PsiDocumentManager.getInstance(project)).isInUncommittedSet(document)) {
        log(project, "Abandon and proceed to next", task);
        return true;
      }

      if (task.isCanceled() || task.dead) {
        return true; // document has been marked as removed, e.g. by synchronous commit
      }

      startNewTask(task, "Pulled new task", true);

      documentsToApplyInEDT.add(task);
    }

    boolean success = false;
    Object failureReason = null;
    try {
      if (!task.isCanceled()) {
        final CommitTask commitTask = task;
        final Ref<Pair<Runnable, Object>> result = new Ref<>();
        ProgressManager.getInstance().executeProcessUnderProgress(() -> result.set(commitUnderProgress(commitTask, false)), task.indicator);
        final Runnable finishRunnable = result.get().first;
        success = finishRunnable != null;
        failureReason = result.get().second;

        if (success) {
          assert !myApplication.isDispatchThread();
          TransactionGuardImpl guard = (TransactionGuardImpl)TransactionGuard.getInstance();
          guard.submitTransaction(task.project, task.myCreationContext, finishRunnable);
        }
      }
    }
    catch (ProcessCanceledException e) {
      cancel(e + " (indicator cancel reason: "+((UserDataHolder)task.indicator).getUserData(CANCEL_REASON)+")", true); // leave queue unchanged
      success = false;
      failureReason = e;
    }
    catch (Throwable e) {
      LOG.error(e); // unrecoverable
      cancel(e, false);
      failureReason = ExceptionUtil.getThrowableText(e);
    }

    if (!success) {
      Project project = task.project;
      Document document = task.document;
      String reQueuedReason = "re-added on failure: " + failureReason;
      ReadAction.run(() -> {
        if (project.isDisposed()) return;
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        if (documentManager.isCommitted(document)) return; // sync commit hasn't intervened
        CharSequence lastCommittedText = documentManager.getLastCommittedText(document);
        PsiFile file = documentManager.getPsiFile(document);
        List<Pair<PsiFileImpl, FileASTNode>> oldFileNodes = file == null ? null : getAllFileNodes(file);
        if (oldFileNodes != null) {
          // somebody's told us explicitly not to beat the dead horse again,
          // e.g. when submitted the same document with the different transaction context
          if (task.dead) return;

          doQueue(project, document, oldFileNodes, reQueuedReason, task.myCreationContext, lastCommittedText);
        }
      });
    }
    synchronized (lock) {
      currentTask = null; // do not cancel, it's being invokeLatered
    }

    return true;
  }

  @Override
  public void commitSynchronously(@NotNull Document document, @NotNull Project project, @NotNull PsiFile psiFile) {
    assert !isDisposed;

    if (!project.isInitialized() && !project.isDefault()) {
      @NonNls String s = project + "; Disposed: "+project.isDisposed()+"; Open: "+project.isOpen();
      try {
        Disposer.dispose(project);
      }
      catch (Throwable ignored) {
        // do not fill log with endless exceptions
      }
      throw new RuntimeException(s);
    }

    List<Pair<PsiFileImpl, FileASTNode>> allFileNodes = getAllFileNodes(psiFile);

    Lock documentLock = getDocumentLock(document);

    CommitTask task;
    synchronized (lock) {
      // synchronized to ensure no new similar tasks can start before we hold the document's lock
      task = createNewTaskAndCancelSimilar(project, document, allFileNodes, SYNC_COMMIT_REASON, TransactionGuard.getInstance().getContextTransaction(),
                                           PsiDocumentManager.getInstance(project).getLastCommittedText(document), true);
    }

    documentLock.lock();
    try {
      assert !task.isCanceled();
      Pair<Runnable, Object> result = commitUnderProgress(task, true);
      Runnable finish = result.first;
      log(project, "Committed sync", task, finish, task.indicator);
      assert finish != null;

      finish.run();
    }
    finally {
      documentLock.unlock();
    }

    // will wake itself up on write action end
  }

  @NotNull
  private static List<Pair<PsiFileImpl, FileASTNode>> getAllFileNodes(@NotNull PsiFile file) {
    if (!file.isValid()) {
      throw new PsiInvalidElementAccessException(file, "File " + file + " is invalid, can't commit");
    }
    if (file instanceof PsiCompiledFile) {
      throw new IllegalArgumentException("Can't commit ClsFile: "+file);
    }

    return ContainerUtil.map(file.getViewProvider().getAllFiles(), root -> Pair.create((PsiFileImpl)root, root.getNode()));
  }

  @NotNull
  private static ProgressIndicator createProgressIndicator() {
    return new StandardProgressIndicatorBase();
  }

  private void startNewTask(@Nullable CommitTask task, @NotNull Object reason, boolean canReQueue) {
    synchronized (lock) { // sync to prevent overwriting
      CommitTask cur = currentTask;
      if (cur != null) {
        cur.cancel(reason, canReQueue);
      }
      currentTask = task;
    }
  }

  // returns (finish commit Runnable (to be invoked later in EDT), null) on success or (null, failure reason) on failure
  @NotNull
  private Pair<Runnable, Object> commitUnderProgress(@NotNull final CommitTask task, final boolean synchronously) {
    if (synchronously) {
      assert !task.isCanceled();
    }

    final Document document = task.getDocument();
    final Project project = task.project;
    final PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);
    final List<BooleanRunnable> finishProcessors = new SmartList<>();
    List<BooleanRunnable> reparseInjectedProcessors = new SmartList<>();
    Runnable runnable = () -> {
      myApplication.assertReadAccessAllowed();
      if (project.isDisposed()) return;

      Lock lock = getDocumentLock(document);
      if (!lock.tryLock()) {
        task.cancel("Can't obtain document lock", true);
        return;
      }

      boolean canceled = false;
      try {
        if (documentManager.isCommitted(document)) return;

        if (!task.isStillValid()) {
          canceled = true;
          return;
        }

        FileViewProvider viewProvider = documentManager.getCachedViewProvider(document);
        if (viewProvider == null) {
          finishProcessors.add(handleCommitWithoutPsi(documentManager, task));
          return;
        }

        for (Pair<PsiFileImpl, FileASTNode> pair : task.myOldFileNodes) {
          PsiFileImpl file = pair.first;
          if (file.isValid()) {
            FileASTNode oldFileNode = pair.second;
            ProperTextRange changedPsiRange = ChangedPsiRangeUtil
              .getChangedPsiRange(file, task.document, task.myLastCommittedText, document.getImmutableCharSequence());
            if (changedPsiRange != null) {
              BooleanRunnable finishProcessor = doCommit(task, file, oldFileNode, changedPsiRange, reparseInjectedProcessors);
              finishProcessors.add(finishProcessor);
            }
          }
          else {
            // file became invalid while sitting in the queue
            if (task.reason.equals(SYNC_COMMIT_REASON)) {
              throw new PsiInvalidElementAccessException(file, "File " + file + " invalidated during sync commit");
            }
            commitAsynchronously(project, document, "File " + file + " invalidated during background commit; task: "+task,
                                 task.myCreationContext);
          }
        }
      }
      finally {
        lock.unlock();
        if (canceled) {
          task.cancel("Task invalidated", false);
        }
      }
    };
    if (!myApplication.tryRunReadAction(runnable)) {
      log(project, "Could not start read action", task, myApplication.isReadAccessAllowed(), Thread.currentThread());
      return new Pair<>(null, "Could not start read action");
    }

    boolean canceled = task.isCanceled();
    assert !synchronously || !canceled;
    if (canceled) {
      return new Pair<>(null, "Indicator was canceled");
    }

    Runnable result = createFinishCommitInEDTRunnable(task, synchronously, finishProcessors, reparseInjectedProcessors);
    return Pair.create(result, null);
  }

  @NotNull
  private Runnable createFinishCommitInEDTRunnable(@NotNull final CommitTask task,
                                                   final boolean synchronously,
                                                   @NotNull List<BooleanRunnable> finishProcessors,
                                                   @NotNull List<BooleanRunnable> reparseInjectedProcessors) {
    return () -> {
      myApplication.assertIsDispatchThread();
      Document document = task.getDocument();
      Project project = task.project;
      PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);
      boolean committed = project.isDisposed() || documentManager.isCommitted(document);
      synchronized (lock) {
        documentsToApplyInEDT.remove(task);
        if (committed) {
          log(project, "Marked as already committed in EDT apply queue, return", task);
          return;
        }
      }

      boolean changeStillValid = task.isStillValid();
      boolean success = changeStillValid && documentManager.finishCommit(document, finishProcessors, reparseInjectedProcessors,
                                                                         synchronously, task.reason);
      if (synchronously) {
        assert success;
      }
      if (!changeStillValid) {
        log(project, "document changed; ignore", task);
        return;
      }
      if (synchronously || success) {
        assert !documentManager.isInUncommittedSet(document);
      }
      if (success) {
        log(project, "Commit finished", task);
      }
      else {
        // add document back to the queue
        commitAsynchronously(project, document, "Re-added back", task.myCreationContext);
      }
    };
  }

  @NotNull
  private BooleanRunnable handleCommitWithoutPsi(@NotNull final PsiDocumentManagerBase documentManager,
                                                     @NotNull final CommitTask task) {
    return () -> {
      log(task.project, "Finishing without PSI", task);
      Document document = task.getDocument();
      if (!task.isStillValid() || documentManager.getCachedViewProvider(document) != null) {
        return false;
      }

      documentManager.handleCommitWithoutPsi(document);
      return true;
    };
  }

  boolean isEnabled() {
    synchronized (lock) {
      return myEnabled;
    }
  }

  @Override
  public String toString() {
    return "Document commit thread; application: "+myApplication+"; isDisposed: "+isDisposed+"; myEnabled: "+isEnabled();
  }

  @TestOnly
  // waits for all tasks in 'documentsToCommit' queue to be finished, i.e. wait
  // - for 'commitUnderProgress' executed for all documents from there,
  // - for (potentially) a number of documents added to 'documentsToApplyInEDT'
  // - for these apply tasks (created in 'createFinishCommitInEDTRunnable') executed in EDT
  // NB: failures applying EDT tasks are not handled - i.e. failed documents are added back to the queue and the method returns
  void waitForAllCommits() throws ExecutionException, InterruptedException, TimeoutException {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();

    ((BoundedTaskExecutor)executor).waitAllTasksExecuted(100, TimeUnit.SECONDS);
    UIUtil.dispatchAllInvocationEvents();
  }

  private static final Key<Object> CANCEL_REASON = Key.create("CANCEL_REASON");
  private class CommitTask {
    @NotNull private final Document document;
    @NotNull final Project project;
    private final int modificationSequence; // store initial document modification sequence here to check if it changed later before commit in EDT

    // when queued it's not started
    // when dequeued it's started
    // when failed it's canceled
    @NotNull final ProgressIndicator indicator; // progress to commit this doc under.
    @NotNull final Object reason;
    @Nullable final TransactionId myCreationContext;
    private final CharSequence myLastCommittedText;
    @NotNull final List<Pair<PsiFileImpl, FileASTNode>> myOldFileNodes;
    private volatile boolean dead; // the task was explicitly removed from the queue; no attempts to re-queue should be made

    CommitTask(@NotNull final Project project,
               @NotNull final Document document,
               @NotNull final List<Pair<PsiFileImpl, FileASTNode>> oldFileNodes,
               @NotNull ProgressIndicator indicator,
               @NotNull Object reason,
               @Nullable TransactionId context,
               @NotNull CharSequence lastCommittedText) {
      this.document = document;
      this.project = project;
      this.indicator = indicator;
      this.reason = reason;
      myCreationContext = context;
      myLastCommittedText = lastCommittedText;
      myOldFileNodes = oldFileNodes;
      modificationSequence = ((DocumentEx)document).getModificationSequence();
    }

    @NonNls
    @Override
    public String toString() {
      Document document = getDocument();
      String indicatorInfo = isCanceled() ? " (Canceled: " + ((UserDataHolder)indicator).getUserData(CANCEL_REASON) + ")" : "";
      String removedInfo = dead ? " (dead)" : "";
      String reasonInfo = " task reason: " + StringUtil.first(String.valueOf(reason), 180, true) +
                          (isStillValid() ? "" : "; changed: old seq=" + modificationSequence + ", new seq=" + ((DocumentEx)document).getModificationSequence());
      String contextInfo = " Context: "+myCreationContext;
      return System.identityHashCode(this)+"; " + indicatorInfo + removedInfo + contextInfo + reasonInfo;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof CommitTask)) return false;

      CommitTask task = (CommitTask)o;

      return Comparing.equal(getDocument(),task.getDocument()) && project.equals(task.project);
    }

    @Override
    public int hashCode() {
      int result = getDocument().hashCode();
      result = 31 * result + project.hashCode();
      return result;
    }

    boolean isStillValid() {
      Document document = getDocument();
      return ((DocumentEx)document).getModificationSequence() == modificationSequence;
    }

    private void cancel(@NotNull Object reason, boolean canReQueue) {
      dead |= !canReQueue; // set the flag before cancelling indicator
      if (!isCanceled()) {
        log(project, "cancel", this, reason);

        indicator.cancel();
        ((UserDataHolder)indicator).putUserData(CANCEL_REASON, reason);

        synchronized (lock) {
          documentsToCommit.remove(this);
          documentsToApplyInEDT.remove(this);
        }
      }
    }

    @NotNull
    Document getDocument() {
      return document;
    }

    private boolean isCanceled() {
      return indicator.isCanceled();
    }
  }

  // returns runnable to execute under write action in AWT to finish the commit, updates "outChangedRange"
  @NotNull
  private static BooleanRunnable doCommit(@NotNull final CommitTask task,
                                          @NotNull final PsiFile file,
                                          @NotNull final FileASTNode oldFileNode,
                                          @NotNull ProperTextRange changedPsiRange,
                                          @NotNull List<? super BooleanRunnable> outReparseInjectedProcessors) {
    Document document = task.getDocument();
    final CharSequence newDocumentText = document.getImmutableCharSequence();

    final Boolean data = document.getUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY);
    if (data != null) {
      document.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null);
      file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, data);
    }

    DiffLog diffLog;
    try (
      BlockSupportImpl.ReparseResult result =
        BlockSupportImpl.reparse(file, oldFileNode, changedPsiRange, newDocumentText, task.indicator, task.myLastCommittedText)) {
      diffLog = result.log;

      PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(task.project);

      List<BooleanRunnable> injectedRunnables =
        documentManager.reparseChangedInjectedFragments(document, file, changedPsiRange, task.indicator, result.oldRoot, result.newRoot);
      outReparseInjectedProcessors.addAll(injectedRunnables);
    }

    return () -> {
      FileViewProvider viewProvider = file.getViewProvider();
      Document document1 = task.getDocument();
      if (!task.isStillValid() ||
          ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(file.getProject())).getCachedViewProvider(document1) != viewProvider) {
        return false; // optimistic locking failed
      }

      if (!ApplicationManager.getApplication().isWriteAccessAllowed()) {
        VirtualFile vFile = viewProvider.getVirtualFile();
        LOG.error("Write action expected" +
                  "; document=" + document1 +
                  "; file=" + file + " of " + file.getClass() +
                  "; file.valid=" + file.isValid() +
                  "; file.eventSystemEnabled=" + viewProvider.isEventSystemEnabled() +
                  "; viewProvider=" + viewProvider + " of " + viewProvider.getClass() +
                  "; language=" + file.getLanguage() +
                  "; vFile=" + vFile + " of " + vFile.getClass() +
                  "; free-threaded=" + AbstractFileViewProvider.isFreeThreaded(viewProvider));
      }

      diffLog.doActualPsiChange(file);

      assertAfterCommit(document1, file, (FileElement)oldFileNode);

      return true;
    };
  }

  private static void assertAfterCommit(@NotNull Document document, @NotNull final PsiFile file, @NotNull FileElement oldFileNode) {
    if (oldFileNode.getTextLength() != document.getTextLength()) {
      final String documentText = document.getText();
      String fileText = file.getText();
      boolean sameText = Comparing.equal(fileText, documentText);
      LOG.error("commitDocument() left PSI inconsistent: " + DebugUtil.diagnosePsiDocumentInconsistency(file, document) +
                "; node.length=" + oldFileNode.getTextLength() +
                "; doc.text" + (sameText ? "==" : "!=") + "file.text" +
                "; file name:" + file.getName()+
                "; type:"+file.getFileType()+
                "; lang:"+file.getLanguage()
                );

      file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, Boolean.TRUE);
      try {
        BlockSupport blockSupport = BlockSupport.getInstance(file.getProject());
        final DiffLog diffLog = blockSupport.reparseRange(file, file.getNode(), new TextRange(0, documentText.length()), documentText, createProgressIndicator(),
                                                          oldFileNode.getText());
        diffLog.doActualPsiChange(file);

        if (oldFileNode.getTextLength() != document.getTextLength()) {
          LOG.error("PSI is broken beyond repair in: " + file);
        }
      }
      finally {
        file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null);
      }
    }
  }

  /**
   * @return an internal lock object to prevent read & write phases of commit from running simultaneously for free-threaded PSI
   */
  private static Lock getDocumentLock(Document document) {
    Lock lock = document.getUserData(DOCUMENT_LOCK);
    return lock != null ? lock : ((UserDataHolderEx)document).putUserDataIfAbsent(DOCUMENT_LOCK, new ReentrantLock());
  }
  private static final Key<Lock> DOCUMENT_LOCK = Key.create("DOCUMENT_LOCK");

  void cancelTasksOnProjectDispose(@NotNull final Project project) {
    synchronized (lock) {
      cancelTasksOnProjectDispose(project, documentsToCommit);
      cancelTasksOnProjectDispose(project, documentsToApplyInEDT);
    }
  }

  private void cancelTasksOnProjectDispose(@NotNull Project project, @NotNull HashSetQueue<CommitTask> queue) {
    for (HashSetQueue.PositionalIterator<CommitTask> iterator = queue.iterator(); iterator.hasNext(); ) {
      CommitTask commitTask = iterator.next();
      if (commitTask.project == project) {
        iterator.remove();
        commitTask.cancel("project is disposed", false);
      }
    }
  }
}
