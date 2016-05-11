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

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Attachment;
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
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.TreeAspectEvent;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLock;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.text.DiffLog;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSetQueue;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.swing.*;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DocumentCommitThread implements Runnable, Disposable, DocumentCommitProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.DocumentCommitThread");

  private final ExecutorService executor = new BoundedTaskExecutor(PooledThreadExecutor.INSTANCE, 1, this);
  private final Object lock = new Object();
  private final HashSetQueue<CommitTask> documentsToCommit = new HashSetQueue<CommitTask>();      // guarded by lock
  private final HashSetQueue<CommitTask> documentsToApplyInEDT = new HashSetQueue<CommitTask>();  // guarded by lock
  private final ApplicationEx myApplication;
  private volatile boolean isDisposed;
  private CommitTask currentTask; // guarded by lock
  private boolean myEnabled; // true if we can do commits. set to false temporarily during the write action.  guarded by lock
  private int runningWriteActions; // accessed in EDT only

  public static DocumentCommitThread getInstance() {
    return (DocumentCommitThread)ServiceManager.getService(DocumentCommitProcessor.class);
  }
  public DocumentCommitThread(final ApplicationEx application) {
    myApplication = application;
    // install listener in EDT to avoid missing events in case we are inside write action right now
    application.invokeLater(new Runnable() {
      @Override
      public void run() {
        assert runningWriteActions == 0;
        if (application.isDisposed()) return;
        assert !application.isWriteAccessAllowed() || application.isUnitTestMode(); // crazy stuff happens in tests, e.g. UIUtil.dispatchInvocationEvents() inside write action
        application.addApplicationListener(new ApplicationAdapter() {
          @Override
          public void beforeWriteActionStart(@NotNull Object action) {
            int writeActionsBefore = runningWriteActions++;
            if (writeActionsBefore == 0) {
              disable("Write action started: " + action);
            }
          }

          @Override
          public void writeActionFinished(@NotNull Object action) {
            // crazy things happen when running tests, like starting write action in one thread but firing its end in the other
            int writeActionsAfter = runningWriteActions = Math.max(0,runningWriteActions-1);
            if (writeActionsAfter == 0) {
              enable("Write action finished: " + action);
            }
            else {
              if (writeActionsAfter < 0) {
                System.err.println("mismatched listeners: " + writeActionsAfter + ";\n==== log==="+log+"\n====end log==="+
                                   ";\n=======threaddump====\n" +
                                   ThreadDumper.dumpThreadsToString()+"\n=====END threaddump=======");
                assert false;
              }
            }
          }
        }, DocumentCommitThread.this);

        enable("Listener installed, started");
      }
    });
  }

  @Override
  public void dispose() {
    isDisposed = true;
    synchronized (lock) {
      documentsToCommit.clear();
    }
    cancel("Stop thread");
  }

  private void disable(@NonNls @NotNull Object reason) {
    // write action has just started, all commits are useless
    synchronized (lock) {
      cancel(reason);
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

  private void wakeUpQueue() {
    if (!isDisposed) {
      executor.execute(this);
    }
  }

  private void cancel(@NonNls @NotNull Object reason) {
    startNewTask(null, reason);
  }

  @Override
  public void commitAsynchronously(@NotNull final Project project,
                                   @NotNull final Document document,
                                   @NonNls @NotNull Object reason,
                                   @NotNull ModalityState currentModalityState) {
    assert !isDisposed : "already disposed";

    if (!project.isInitialized()) return;
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getCachedPsiFile(document);
    if (psiFile == null) return;

    doQueue(project, document, getAllFileNodes(psiFile), reason, currentModalityState);
  }

  @NotNull
  private CommitTask doQueue(@NotNull Project project,
                             @NotNull Document document,
                             @NotNull List<Pair<PsiFileImpl, FileASTNode>> oldFileNodes,
                             @NotNull Object reason,
                             @NotNull ModalityState currentModalityState) {
    synchronized (lock) {
      CommitTask newTask = createNewTaskAndCancelSimilar(project, document, oldFileNodes, reason, currentModalityState);

      documentsToCommit.offer(newTask);
      log(project, "Queued", newTask, reason);

      wakeUpQueue();
      return newTask;
    }
  }

  @NotNull
  private CommitTask createNewTaskAndCancelSimilar(@NotNull Project project,
                                                   @NotNull Document document,
                                                   @NotNull List<Pair<PsiFileImpl, FileASTNode>> oldFileNodes,
                                                   @NotNull Object reason,
                                                   @NotNull ModalityState currentModalityState) {
    synchronized (lock) {
      for (Pair<PsiFileImpl, FileASTNode> pair : oldFileNodes) {
        assert pair.first.getProject() == project;
      }
      CommitTask newTask = new CommitTask(project, document, oldFileNodes, createProgressIndicator(), reason, currentModalityState);
      cancelAndRemoveFromDocsToCommit(newTask, reason);
      cancelAndRemoveCurrentTask(newTask, reason);
      cancelAndRemoveFromDocsToApplyInEDT(newTask, reason);

      return newTask;
    }
  }

  final StringBuilder log = new StringBuilder();

  @SuppressWarnings({"NonConstantStringShouldBeStringBuffer", "StringConcatenationInLoop"})
  public void log(Project project, @NonNls String msg, @Nullable CommitTask task, @NonNls Object... args) {
    if (true) return;

    String indent = new SimpleDateFormat("hh:mm:ss:SSSS").format(new Date()) +
      (SwingUtilities.isEventDispatchThread() ?        "-(EDT) " :
                                                       "-(" + Thread.currentThread()+ " ");
    @NonNls
    String s = indent + msg + (task == null ? " - " : "; task: " + task);

    for (Object arg : args) {
      if (!StringUtil.isEmpty(String.valueOf(arg))) {
        s += "; "+arg;
        if (arg instanceof Document) {
          Document document = (Document)arg;
          s+= " (\""+StringUtil.first(document.getImmutableCharSequence(), 40, true).toString().replaceAll("\n", " ")+"\")";
        }
      }
    }
    if (task != null) {
      if (task.indicator.isCanceled()) {
        s += "; indicator: " + task.indicator;
      }
      boolean stillUncommitted = !task.project.isDisposed() &&
                                 ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(task.project)).isInUncommittedSet(task.document);
      if (stillUncommitted) {
        s += "; still uncommitted";
      }

      Set<Document> uncommitted = project == null || project.isDisposed() ? Collections.<Document>emptySet() :
                                  ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(project)).myUncommittedDocuments;
      if (!uncommitted.isEmpty()) {
        s+= "; uncommitted: "+uncommitted;
      }
    }
    synchronized (lock) {
      int size = documentsToCommit.size();
      if (size != 0) {
        s += " (" + size + " documents still in queue: ";
        int i = 0;
        for (CommitTask commitTask : documentsToCommit) {
          s += commitTask + "; ";
          if (++i > 4) {
            s += " ... ";
            break;
          }
        }
        s += ")";
      }
    }

    System.out.println(s);

    synchronized (log) {
      log.append(s).append("\n");
      if (log.length() > 100000) {
        log.delete(0, log.length()-50000);
      }
    }
  }


  // cancels all pending commits
  @TestOnly
  private void cancelAll() {
    synchronized (lock) {
      String reason = "Cancel all in tests";
      cancel(reason);
      for (CommitTask commitTask : documentsToCommit) {
        commitTask.cancel(reason, this);
        log(commitTask.project, "Removed from background queue", commitTask);
      }
      documentsToCommit.clear();
      for (CommitTask commitTask : documentsToApplyInEDT) {
        commitTask.cancel(reason, this);
        log(commitTask.project, "Removed from EDT apply queue (sync commit called)", commitTask);
      }
      documentsToApplyInEDT.clear();
      CommitTask task = currentTask;
      if (task != null) {
        cancelAndRemoveFromDocsToCommit(task, reason);
      }
      cancel("Sync commit intervened");
      ((BoundedTaskExecutor)executor).clearAndCancelAll();
    }
  }

  @TestOnly
  public void clearQueue() {
    cancelAll();
    clearLog();
    wakeUpQueue();
  }

  private void clearLog() {
    synchronized (log) {
      log.setLength(0);
    }
  }

  private void cancelAndRemoveCurrentTask(@NotNull CommitTask newTask, @NotNull Object reason) {
    CommitTask currentTask = this.currentTask;
    if (currentTask != null && currentTask.equals(newTask)) {
      cancelAndRemoveFromDocsToCommit(currentTask, reason);
      cancel(reason);
    }
  }

  private void cancelAndRemoveFromDocsToApplyInEDT(@NotNull CommitTask newTask, @NotNull Object reason) {
    boolean removed = cancelAndRemoveFromQueue(newTask, documentsToApplyInEDT, reason);
    if (removed) {
      log(newTask.project, "Removed from EDT apply queue", newTask);
    }
  }

  private void cancelAndRemoveFromDocsToCommit(@NotNull final CommitTask newTask, @NotNull Object reason) {
    boolean removed = cancelAndRemoveFromQueue(newTask, documentsToCommit, reason);
    if (removed) {
      log(newTask.project, "Removed from background queue", newTask);
    }
  }

  private static boolean cancelAndRemoveFromQueue(@NotNull CommitTask newTask, @NotNull HashSetQueue<CommitTask> queue, @NotNull Object reason) {
    CommitTask queuedTask = queue.find(newTask);
    if (queuedTask != null) {
      assert queuedTask != newTask;
      queuedTask.cancel(reason, getInstance());
    }
    return queue.remove(newTask);
  }

  @Override
  public void run() {
    while (!isDisposed) {
      try {
        boolean polled = pollQueue();
        if (!polled) break;
      }
      catch(Throwable e) {
        LOG.error(e);
      }
    }
  }

  // returns true if queue changed
  private boolean pollQueue() {
    assert !myApplication.isDispatchThread() : Thread.currentThread();
    boolean success = false;
    Document document = null;
    Project project = null;
    CommitTask task = null;
    Object failureReason = null;
    try {
      ProgressIndicator indicator;
      synchronized (lock) {
        if (!myEnabled || (task = documentsToCommit.poll()) == null) {
          return false;
        }

        document = task.document;
        indicator = task.indicator;
        project = task.project;

        if (project.isDisposed() || !((PsiDocumentManagerBase)PsiDocumentManager.getInstance(project)).isInUncommittedSet(document)) {
          log(project, "Abandon and proceed to next", task);
          return true;
        }

        if (indicator.isCanceled()) {
          return true; // document has been marked as removed, e.g. by synchronous commit
        }

        startNewTask(task, "Pulled new task");

        // transfer to documentsToApplyInEDT
        documentsToApplyInEDT.add(task);
      }

      Runnable finishRunnable = null;
      if (indicator.isCanceled()) {
        success = false;
      }
      else {
        final CommitTask commitTask = task;
        final Ref<Pair<Runnable, Object>> result = new Ref<Pair<Runnable, Object>>();
        ProgressManager.getInstance().executeProcessUnderProgress(new Runnable() {
          @Override
          public void run() {
            result.set(commitUnderProgress(commitTask, false));
          }
        }, commitTask.indicator);
        finishRunnable = result.get().first;
        success = finishRunnable != null;
        failureReason = result.get().second;
      }

      if (success) {
        assert !myApplication.isDispatchThread();
        final Runnable finalFinishRunnable = finishRunnable;
        final Project finalProject = project;
        final TransactionGuardImpl guard = (TransactionGuardImpl)TransactionGuard.getInstance();
        final TransactionId transaction = guard.getModalityTransaction(task.myCreationModalityState);
        // invokeLater can be removed once transactions are enforced
        myApplication.invokeLater(new Runnable() {
          @Override
          public void run() {
            guard.submitTransaction(finalProject, transaction, finalFinishRunnable);
          }
        }, task.myCreationModalityState);
      }
    }
    catch (ProcessCanceledException e) {
      cancel(e + " (cancel reason: "+((UserDataHolder)task.indicator).getUserData(CommitTask.CANCEL_REASON)+")"); // leave queue unchanged
      success = false;
      failureReason = e;
    }
    catch (Throwable e) {
      LOG.error(log.toString(), e);
      cancel(e);
      failureReason = ExceptionUtil.getThrowableText(e);
    }

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    if (!success && task != null && documentManager.isUncommited(task.document)) { // sync commit has not intervened
      final Document finalDocument = document;
      final Project finalProject = project;
      List<Pair<PsiFileImpl, FileASTNode>> oldFileNodes =
        ApplicationManager.getApplication().runReadAction(new Computable<List<Pair<PsiFileImpl, FileASTNode>>>() {
          @Override
          public List<Pair<PsiFileImpl, FileASTNode>> compute() {
            PsiFile file = finalProject.isDisposed() ? null : documentManager.getPsiFile(finalDocument);
            return file == null ? null : getAllFileNodes(file);
          }
        });
      if (oldFileNodes != null) {
        doQueue(project, document, oldFileNodes, "re-added on failure: "+failureReason, task.myCreationModalityState);
      }
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
      task = createNewTaskAndCancelSimilar(project, document, allFileNodes, "Sync commit", ModalityState.current());
      documentLock.lock();
    }

    try {
      assert !task.indicator.isCanceled();
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
    return ContainerUtil.map(file.getViewProvider().getAllFiles(), new Function<PsiFile, Pair<PsiFileImpl, FileASTNode>>() {
      @Override
      public Pair<PsiFileImpl, FileASTNode> fun(PsiFile root) {
        return Pair.create((PsiFileImpl)root, root.getNode());
      }
    });
  }

  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StandardProgressIndicatorBase();
  }

  private void startNewTask(@Nullable CommitTask task, @NotNull Object reason) {
    synchronized (lock) { // sync to prevent overwriting
      CommitTask cur = currentTask;
      if (cur != null) {
        cur.cancel(reason, this);
      }
      currentTask = task;
    }
  }

  // returns (finish commit Runnable (to be invoked later in EDT), null) on success or (null, failure reason) on failure
  @NotNull
  private Pair<Runnable, Object> commitUnderProgress(@NotNull final CommitTask task, final boolean synchronously) {
    if (synchronously) {
      assert !task.indicator.isCanceled();
    }
    final Project project = task.project;

    final Document document = task.document;
    final PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);
    final List<Processor<Document>> finishProcessors = new SmartList<Processor<Document>>();
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        myApplication.assertReadAccessAllowed();
        if (project.isDisposed()) return;

        Lock lock = getDocumentLock(document);
        if (!lock.tryLock()) {
          task.cancel("Can't obtain document lock", DocumentCommitThread.this);
          return;
        }

        try {
          if (documentManager.isCommitted(document)) return;

          if (!task.isStillValid()) {
            task.cancel("Task invalidated", DocumentCommitThread.this);
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
              Processor<Document> finishProcessor = doCommit(task, file, oldFileNode);
              if (finishProcessor != null) {
                finishProcessors.add(finishProcessor);
              }
            }
          }
        }
        finally {
          lock.unlock();
        }
      }
    };
    if (synchronously) {
      runnable.run();
    }
    else if (!myApplication.tryRunReadAction(runnable)) {
      log(project, "Could not start read action", task, myApplication.isReadAccessAllowed(), Thread.currentThread());
      return new Pair<Runnable, Object>(null, "Could not start read action");
    }

    boolean canceled = task.indicator.isCanceled();
    assert !synchronously || !canceled;
    if (canceled) {
      return new Pair<Runnable, Object>(null, "Indicator was canceled");
    }

    Runnable result = new Runnable() {
      @Override
      public void run() {
        myApplication.assertIsDispatchThread();

        boolean committed = project.isDisposed() || documentManager.isCommitted(document);
        synchronized (lock) {
          documentsToApplyInEDT.remove(task);
          if (committed) {
            log(project, "Marked as already committed in EDT apply queue, return", task);
            return;
          }
        }

        try {
          boolean changeStillValid = task.isStillValid();
          boolean success = changeStillValid && documentManager.finishCommit(document, finishProcessors, synchronously, task.reason);
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
            commitAsynchronously(project, document, "Re-added back", task.myCreationModalityState);
          }
        }
        catch (Error e) {
          System.err.println("Log:" + log);
          throw e;
        }
      }
    };
    return Pair.create(result, null);
  }

  @NotNull
  private Processor<Document> handleCommitWithoutPsi(@NotNull final PsiDocumentManagerBase documentManager,
                                                     @NotNull final CommitTask task) {
    return new Processor<Document>() {
      @Override
      public boolean process(Document document) {
        log(task.project, "Finishing without PSI", task);
        if (!task.isStillValid() || documentManager.getCachedViewProvider(document) != null) {
          return false;
        }

        documentManager.handleCommitWithoutPsi(document);
        return true;
      }
    };
  }

  boolean isEnabled() {
    synchronized (lock) {
      return myEnabled;
    }
  }

  @Override
  public String toString() {
    return "Document commit thread; application: "+myApplication+"; isDisposed: "+isDisposed+"; myEnabled: "+isEnabled()+"; runningWriteActions: "+runningWriteActions;
  }

  @TestOnly
  public void waitForAllCommits() throws ExecutionException, InterruptedException, TimeoutException {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();

    ((BoundedTaskExecutor)executor).waitAllTasksExecuted(100, TimeUnit.SECONDS);
    UIUtil.dispatchAllInvocationEvents();
  }

  private static class CommitTask {
    static final Key<Object> CANCEL_REASON = Key.create("CANCEL_REASON");
    @NotNull final Document document;
    @NotNull final Project project;
    private final int modificationSequence; // store initial document modification sequence here to check if it changed later before commit in EDT

    // when queued it's not started
    // when dequeued it's started
    // when failed it's canceled
    @NotNull final ProgressIndicator indicator; // progress to commit this doc under.
    @NotNull final Object reason;
    @NotNull final ModalityState myCreationModalityState;
    private final CharSequence myLastCommittedText;
    @NotNull final List<Pair<PsiFileImpl, FileASTNode>> myOldFileNodes;

    protected CommitTask(@NotNull final Project project,
                         @NotNull final Document document,
                         @NotNull final List<Pair<PsiFileImpl, FileASTNode>> oldFileNodes,
                         @NotNull ProgressIndicator indicator,
                         @NotNull Object reason,
                         @NotNull ModalityState currentModalityState) {
      this.document = document;
      this.project = project;
      this.indicator = indicator;
      this.reason = reason;
      myCreationModalityState = currentModalityState;
      myLastCommittedText = PsiDocumentManager.getInstance(project).getLastCommittedText(document);
      myOldFileNodes = oldFileNodes;
      modificationSequence = ((DocumentEx)document).getModificationSequence();
    }

    @NonNls
    @Override
    public String toString() {
      return "Doc: " + document + " (\"" + StringUtil.first(document.getImmutableCharSequence(), 40, true).toString().replaceAll("\n", " ") + "\")"
             + (indicator.isCanceled() ? " (Canceled: " + ((UserDataHolder)indicator).getUserData(CANCEL_REASON) + ")":"")
             +" Reason: " + reason
             + (isStillValid() ? "" : "; changed: old seq="+modificationSequence+", new seq="+ ((DocumentEx)document).getModificationSequence())
        ;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof CommitTask)) return false;

      CommitTask task = (CommitTask)o;

      return document.equals(task.document) && project.equals(task.project);
    }

    @Override
    public int hashCode() {
      int result = document.hashCode();
      result = 31 * result + project.hashCode();
      return result;
    }

    public boolean isStillValid() {
      return ((DocumentEx)document).getModificationSequence() == modificationSequence;
    }

    private void cancel(@NotNull Object reason, @NotNull DocumentCommitThread commitProcessor) {
      if (!indicator.isCanceled()) {
        commitProcessor.log(project, "indicator cancel", this);

        indicator.cancel();
        ((UserDataHolder)indicator).putUserData(CANCEL_REASON, reason);
      }
    }
  }

  // public for Upsource
  @Nullable("returns runnable to execute under write action in AWT to finish the commit")
  public Processor<Document> doCommit(@NotNull final CommitTask task,
                                      @NotNull final PsiFile file,
                                      @NotNull final FileASTNode oldFileNode) {
    Document document = task.document;
    final CharSequence newDocumentText = document.getImmutableCharSequence();
    final TextRange changedPsiRange = getChangedPsiRange(file, task.myLastCommittedText, newDocumentText);
    if (changedPsiRange == null) {
      return null;
    }

    final Boolean data = document.getUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY);
    if (data != null) {
      document.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null);
      file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, data);
    }

    BlockSupport blockSupport = BlockSupport.getInstance(file.getProject());
    final DiffLog diffLog = blockSupport.reparseRange(file, oldFileNode, changedPsiRange, newDocumentText, task.indicator, task.myLastCommittedText);

    return new Processor<Document>() {
      @Override
      public boolean process(Document document) {
        FileViewProvider viewProvider = file.getViewProvider();
        if (!task.isStillValid() ||
            ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(file.getProject())).getCachedViewProvider(document) != viewProvider) {
          return false; // optimistic locking failed
        }

        if (file.isPhysical() && !ApplicationManager.getApplication().isWriteAccessAllowed()) {
          VirtualFile vFile = viewProvider.getVirtualFile();
          LOG.error("Write action expected" +
                    "; file=" + file + " of " + file.getClass() +
                    "; file.valid=" + file.isValid() +
                    "; file.eventSystemEnabled=" + viewProvider.isEventSystemEnabled() +
                    "; language=" + file.getLanguage() +
                    "; vFile=" + vFile + " of " + vFile.getClass() +
                    "; free-threaded=" + PsiDocumentManagerBase.isFreeThreaded(vFile));
        }

        doActualPsiChange(file, diffLog);

        assertAfterCommit(document, file, (FileElement)oldFileNode);

        return true;
      }
    };
  }

  private static int getLeafMatchingLength(CharSequence leafText, CharSequence pattern, int patternIndex, int finalPatternIndex, int direction) {
    int leafIndex = direction == 1 ? 0 : leafText.length() - 1;
    int finalLeafIndex = direction == 1 ? leafText.length() - 1 : 0;
    int result = 0;
    while (leafText.charAt(leafIndex) == pattern.charAt(patternIndex)) {
      result++;
      if (leafIndex == finalLeafIndex || patternIndex == finalPatternIndex) {
        break;
      }
      leafIndex += direction;
      patternIndex += direction;
    }
    return result;
  }

  private static int getMatchingLength(@NotNull FileElement treeElement, @NotNull CharSequence text, boolean fromStart) {
    int patternIndex = fromStart ? 0 : text.length() - 1;
    int finalPatternIndex = fromStart ? text.length() - 1 : 0;
    int direction = fromStart ? 1 : -1;
    ASTNode leaf = fromStart ? TreeUtil.findFirstLeaf(treeElement, false) : TreeUtil.findLastLeaf(treeElement, false);
    int result = 0;
    while (leaf != null && (fromStart ? patternIndex <= finalPatternIndex : patternIndex >= finalPatternIndex)) {
      if (!(leaf instanceof ForeignLeafPsiElement)) {
        CharSequence chars = leaf.getChars();
        if (chars.length() > 0) {
          int matchingLength = getLeafMatchingLength(chars, text, patternIndex, finalPatternIndex, direction);
          result += matchingLength;
          if (matchingLength != chars.length()) {
            break;
          }
          patternIndex += fromStart ? matchingLength : -matchingLength;
        }
      }
      leaf = fromStart ? TreeUtil.nextLeaf(leaf, false) : TreeUtil.prevLeaf(leaf, false);
    }
    return result;
  }

  @Nullable
  public static TextRange getChangedPsiRange(@NotNull PsiFile file, @NotNull FileElement treeElement, @NotNull CharSequence newDocumentText) {
    int psiLength = treeElement.getTextLength();
    if (!file.getViewProvider().supportsIncrementalReparse(file.getLanguage())) {
      return new TextRange(0, psiLength);
    }

    int commonPrefixLength = getMatchingLength(treeElement, newDocumentText, true);
    if (commonPrefixLength == newDocumentText.length() && newDocumentText.length() == psiLength) {
      return null;
    }

    int commonSuffixLength = Math.min(getMatchingLength(treeElement, newDocumentText, false), psiLength - commonPrefixLength);
    return new TextRange(commonPrefixLength, psiLength - commonSuffixLength);
  }

  @Nullable
  private static TextRange getChangedPsiRange(@NotNull PsiFile file,
                                              @NotNull CharSequence oldDocumentText,
                                              @NotNull CharSequence newDocumentText) {
    int psiLength = oldDocumentText.length();
    if (!file.getViewProvider().supportsIncrementalReparse(file.getLanguage())) {
      return new TextRange(0, psiLength);
    }

    int commonPrefixLength = StringUtil.commonPrefixLength(oldDocumentText, newDocumentText);
    if (commonPrefixLength == newDocumentText.length() && newDocumentText.length() == psiLength) {
      return null;
    }

    int commonSuffixLength = Math.min(StringUtil.commonSuffixLength(oldDocumentText, newDocumentText), psiLength - commonPrefixLength);
    return new TextRange(commonPrefixLength, psiLength - commonSuffixLength);
  }

  public static void doActualPsiChange(@NotNull final PsiFile file, @NotNull final DiffLog diffLog) {
    CodeStyleManager.getInstance(file.getProject()).performActionWithFormatterDisabled(new Runnable() {
      @Override
      public void run() {
        synchronized (PsiLock.LOCK) {
          file.getViewProvider().beforeContentsSynchronized();

          final Document document = file.getViewProvider().getDocument();
          PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(file.getProject());
          PsiToDocumentSynchronizer.DocumentChangeTransaction transaction = documentManager.getSynchronizer().getTransaction(document);

          final PsiFileImpl fileImpl = (PsiFileImpl)file;

          if (transaction == null) {
            final PomModel model = PomManager.getModel(fileImpl.getProject());

            model.runTransaction(new PomTransactionBase(fileImpl, model.getModelAspect(TreeAspect.class)) {
              @Override
              public PomModelEvent runInner() {
                return new TreeAspectEvent(model, diffLog.performActualPsiChange(file));
              }
            });
          }
          else {
            diffLog.performActualPsiChange(file);
          }
        }
      }
    });
  }

  private void assertAfterCommit(@NotNull Document document, @NotNull final PsiFile file, @NotNull FileElement oldFileNode) {
    if (oldFileNode.getTextLength() != document.getTextLength()) {
      final String documentText = document.getText();
      String fileText = file.getText();
      boolean sameText = Comparing.equal(fileText, documentText);
      LOG.error("commitDocument() left PSI inconsistent: " + DebugUtil.diagnosePsiDocumentInconsistency(file, document) +
                "; node.length=" + oldFileNode.getTextLength() +
                "; doc.text" + (sameText ? "==" : "!=") + "file.text",
                new Attachment("file psi text", fileText),
                new Attachment("old text", documentText));

      file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, Boolean.TRUE);
      try {
        BlockSupport blockSupport = BlockSupport.getInstance(file.getProject());
        final DiffLog diffLog = blockSupport.reparseRange(file, file.getNode(), new TextRange(0, documentText.length()), documentText, createProgressIndicator(),
                                                          oldFileNode.getText());
        doActualPsiChange(file, diffLog);

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
}
