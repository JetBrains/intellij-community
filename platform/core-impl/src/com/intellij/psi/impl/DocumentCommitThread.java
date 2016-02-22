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

import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
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

public class DocumentCommitThread extends DocumentCommitProcessor implements Runnable, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.DocumentCommitThread");

  private final ExecutorService executor = new BoundedTaskExecutor(PooledThreadExecutor.INSTANCE, JobSchedulerImpl.CORES_COUNT, this);
  private final Object lock = new Object();
  private final HashSetQueue<CommitTask> documentsToCommit = new HashSetQueue<CommitTask>();      // guarded by lock
  private final HashSetQueue<CommitTask> documentsToApplyInEDT = new HashSetQueue<CommitTask>();  // guarded by lock
  private final ApplicationEx myApplication;
  private volatile boolean isDisposed;
  private CommitTask currentTask; // guarded by lock
  private boolean myEnabled; // true if we can do commits. set to false temporarily during the write action.  guarded by lock
  private int runningWriteActions; // accessed in EDT only

  public static DocumentCommitThread getInstance() {
    return ServiceManager.getService(DocumentCommitThread.class);
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
          public void beforeWriteActionStart(Object action) {
            int writeActionsBefore = runningWriteActions++;
            if (writeActionsBefore == 0) {
              disable("Write action started: " + action);
            }
          }

          @Override
          public void writeActionFinished(Object action) {
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
  }

  private void enable(@NonNls @NotNull Object reason) {
    synchronized (lock) {
      myEnabled = true;
      wakeUpQueue();
    }
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
    queueCommit(project, document, reason, currentModalityState);
  }

  void queueCommit(@NotNull final Project project,
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
      cancelAndRemoveFromDocsToCommit(newTask);
      cancelAndRemoveCurrentTask(newTask);
      cancelAndRemoveFromDocsToApplyInEDT(newTask);

      return newTask;
    }
  }

  final StringBuilder log = new StringBuilder();

  @Override
  public void log(Project project, @NonNls String msg, @Nullable CommitTask task, @NonNls Object... args) {
    if (true) return;

    String indent = new SimpleDateFormat("hh:mm:ss:SSSS").format(new Date()) +
      (SwingUtilities.isEventDispatchThread() ?        "-(EDT) " :
                                                       "-      ");
    @NonNls
    String s = indent + msg + (task == null ? " - " : "; task: " + task+" ("+System.identityHashCode(task)+")");

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
      cancel("cancel all in tests");
      for (CommitTask commitTask : documentsToCommit) {
        commitTask.indicator.cancel();
        log(commitTask.project, "Removed from background queue", commitTask);
      }
      documentsToCommit.clear();
      for (CommitTask commitTask : documentsToApplyInEDT) {
        commitTask.indicator.cancel();
        log(commitTask.project, "Removed from EDT apply queue (sync commit called)", commitTask);
      }
      documentsToApplyInEDT.clear();
      CommitTask task = currentTask;
      if (task != null) {
        cancelAndRemoveFromDocsToCommit(task);
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

  private void cancelAndRemoveCurrentTask(@NotNull CommitTask newTask) {
    CommitTask currentTask = this.currentTask;
    if (currentTask != null && currentTask.equals(newTask)) {
      cancelAndRemoveFromDocsToCommit(currentTask);
      cancel("Sync commit intervened");
    }
  }

  private void cancelAndRemoveFromDocsToApplyInEDT(@NotNull CommitTask newTask) {
    boolean removed = cancelAndRemoveFromQueue(newTask, documentsToApplyInEDT);
    if (removed) {
      log(newTask.project, "Removed from EDT apply queue", newTask);
    }
  }

  private void cancelAndRemoveFromDocsToCommit(@NotNull final CommitTask newTask) {
    boolean removed = cancelAndRemoveFromQueue(newTask, documentsToCommit);
    if (removed) {
      log(newTask.project, "Removed from background queue", newTask);
    }
  }

  private static boolean cancelAndRemoveFromQueue(@NotNull CommitTask newTask, @NotNull HashSetQueue<CommitTask> queue) {
    CommitTask queuedTask = queue.find(newTask);
    if (queuedTask != null) {
      assert queuedTask != newTask;
      queuedTask.indicator.cancel();
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
        final Runnable[] result = new Runnable[1];
        ProgressManager.getInstance().executeProcessUnderProgress(new Runnable() {
          @Override
          public void run() {
            result[0] = commitUnderProgress(commitTask, false);
          }
        }, commitTask.indicator);
        finishRunnable = result[0];
        success = finishRunnable != null;
      }

      if (success) {
        assert !myApplication.isDispatchThread();
        myApplication.invokeLater(finishRunnable, task.myCreationModalityState);
      }
    }
    catch (ProcessCanceledException e) {
      cancel(e); // leave queue unchanged
      log(project, "PCE", task, e);
      success = false;
    }
    catch (Throwable e) {
      LOG.error(log.toString(), e);
      cancel(e);
    }

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    if (!success && task != null && documentManager.isUncommited(task.document)) { // sync commit has not intervened
      final Document finalDocument = document;
      List<Pair<PsiFileImpl, FileASTNode>> oldFileNodes =
        ApplicationManager.getApplication().runReadAction(new Computable<List<Pair<PsiFileImpl, FileASTNode>>>() {
          @Override
          public List<Pair<PsiFileImpl, FileASTNode>> compute() {
            PsiFile file = documentManager.getPsiFile(finalDocument);
            return file == null ? null : getAllFileNodes(file);
          }
        });
      if (oldFileNodes != null) {
        doQueue(project, document, oldFileNodes, "re-added on failure", task.myCreationModalityState);
      }
    }
    synchronized (lock) {
      currentTask = null; // do not cancel, it's being invokeLatered
    }

    return true;
  }

  @Override
  public void commitSynchronously(@NotNull Document document, @NotNull Project project) {
    assert !isDisposed;
    myApplication.assertWriteAccessAllowed();

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

    PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);
    PsiFile psiFile = documentManager.getPsiFile(document);
    if (psiFile == null) {
      documentManager.myUncommittedDocuments.remove(document);
      return; // the project must be closing or file deleted
    }
    CommitTask task = createNewTaskAndCancelSimilar(project, document, getAllFileNodes(psiFile), "Sync commit", ModalityState.current());
    assert !task.indicator.isCanceled();
    Runnable finish = commitUnderProgress(task, true);
    log(project, "Committed sync", task, finish, task.indicator);
    assert finish != null;

    finish.run();

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
  @Override
  protected ProgressIndicator createProgressIndicator() {
    return ApplicationManager.getApplication().isUnitTestMode() ? new AbstractProgressIndicatorBase() {
      @Override
      public void cancel() {
        super.cancel();
        log(null, "indicator cancel", null, this);
      }
    } : new StandardProgressIndicatorBase();
  }

  private void startNewTask(@Nullable CommitTask task, @NotNull Object reason) {
    synchronized (lock) { // sync to prevent overwriting
      CommitTask cur = currentTask;
      if (cur != null) {
        cur.indicator.cancel();
      }
      currentTask = task;
    }
  }

  // returns finish commit Runnable (to be invoked later in EDT), or null on failure
  @Nullable
  private Runnable commitUnderProgress(@NotNull final CommitTask task, final boolean synchronously) {
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

        if (documentManager.isCommitted(document)) return;

        if (!task.isStillValid()) {
          log(project, "Doc changed", task);
          task.indicator.cancel();
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
    };
    if (synchronously) {
      myApplication.assertWriteAccessAllowed();
      runnable.run();
    }
    else if (!myApplication.tryRunReadAction(runnable)) {
      log(project, "Could not start read action", task, myApplication.isReadAccessAllowed(), Thread.currentThread());
      return null;
    }

    boolean canceled = task.indicator.isCanceled();
    assert !synchronously || !canceled;
    if (canceled) {
      return null;
    }

    return new Runnable() {
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
          if (!success) {
            // add document back to the queue
            queueCommit(project, document, "Re-added back", task.myCreationModalityState);
          }
        }
        catch (Error e) {
          System.err.println("Log:"+log);
          throw e;
        }
      }
    };
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
  public void waitForAllCommits() throws ExecutionException, InterruptedException {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();

    ((BoundedTaskExecutor)executor).waitAllTasksExecuted(100, TimeUnit.SECONDS);
    UIUtil.dispatchAllInvocationEvents();
  }
}
