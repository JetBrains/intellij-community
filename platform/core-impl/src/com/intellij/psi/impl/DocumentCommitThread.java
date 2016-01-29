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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.containers.Queue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.swing.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class DocumentCommitThread extends DocumentCommitProcessor implements Runnable, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.DocumentCommitThread");

  private final ExecutorService executor = new BoundedTaskExecutor(PooledThreadExecutor.INSTANCE, JobSchedulerImpl.CORES_COUNT, this);

  private final Queue<CommitTask> documentsToCommit = new Queue<CommitTask>(10);
  private final List<CommitTask> documentsToApplyInEDT = new ArrayList<CommitTask>(10);  // guarded by documentsToCommit
  private final ApplicationEx myApplication;
  private volatile boolean isDisposed;
  private CommitTask currentTask; // guarded by documentsToCommit
  private volatile boolean myEnabled; // true if we can do commits. set to false temporarily during the write action.
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
        assert !application.isWriteAccessAllowed() || application.isUnitTestMode(); // some crazy stuff happens in tests, e.g. UIUtil.dispatchInvocationEvents()
        application.addApplicationListener(new ApplicationAdapter() {
          @Override
          public void beforeWriteActionStart(Object action) {
            int writeActionsBefore = runningWriteActions++;
            if (writeActionsBefore == 0) {
              disable("Write action started: " + action);
            }
            else {
              log("before write action: " + action + "; " + writeActionsBefore + " write actions already running", null, false);
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
              log("after write action: " + action + "; " + writeActionsAfter + " write actions still running", null, false);
              if (writeActionsAfter < 0) {
                System.err.println("mismatched listeners: " + writeActionsAfter + ";\n==== log==="+log+"\n====end log==="+
                                   ";\n=======threaddump====\n" +
                                   ThreadDumper.dumpThreadsToString()+"\n=====END threaddump=======");
                clearLog();
                assert false;
              }
            }
          }
        }, DocumentCommitThread.this);

        enable("Listener installed, started");
      }
    });
    log("Starting thread", null, false);
  }

  @Override
  public void dispose() {
    isDisposed = true;
    synchronized (documentsToCommit) {
      documentsToCommit.clear();
    }
    cancel("Stop thread");
  }

  private void disable(@NonNls Object reason) {
    // write action has just started, all commits are useless
    cancel(reason);
    myEnabled = false;
    log("Disabled", null, false, reason);
  }

  private void enable(@NonNls Object reason) {
    myEnabled = true;
    wakeUpQueue();
    log("Enabled", null, false, reason);
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

    doQueue(project, document, reason, currentModalityState);
  }

  private void doQueue(@NotNull Project project, @NotNull Document document, @NotNull Object reason, @NotNull ModalityState currentModalityState) {
    synchronized (documentsToCommit) {
      ProgressIndicator indicator = createProgressIndicator();
      CommitTask newTask = new CommitTask(document, project, indicator, reason, currentModalityState);

      markRemovedFromDocsToCommit(newTask);
      markRemovedCurrentTask(newTask);
      removeFromDocsToApplyInEDT(newTask);

      documentsToCommit.addLast(newTask);
      log("Queued", newTask, false, reason);

      wakeUpQueue();
    }
  }

  final StringBuilder log = new StringBuilder();

  @Override
  public void log(@NonNls String msg, @Nullable CommitTask task, boolean synchronously, @NonNls Object... args) {
    if (true) return;

    String indent = new SimpleDateFormat("hh:mm:ss:SSSS").format(new Date()) +
      (SwingUtilities.isEventDispatchThread() ?        "-(EDT) " :
                                                       "-      ");
    @NonNls
    String s = indent +
               msg + (synchronously ? " (sync)" : "") +
               (task == null ? " - " : "; task: " + task+" ("+System.identityHashCode(task)+")");

    for (Object arg : args) {
      if (!StringUtil.isEmpty(String.valueOf(arg))) {
        s += "; "+arg;
      }
    }
    if (task != null) {
      boolean stillUncommitted = !task.project.isDisposed() &&
                                 ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(task.project)).isInUncommittedSet(task.document);
      if (stillUncommitted) {
        s += "; Uncommitted: " + task.document;
      }
    }
    synchronized (documentsToCommit) {
      int size = documentsToCommit.size();
      if (size != 0) {
        s += " (" + size + " documents are still in queue)";
      }
    }

    //System.out.println(s);

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
    synchronized (documentsToCommit) {
      cancel("cancel all in tests");
      markRemovedFromDocsToCommit(null);
      documentsToCommit.clear();
      removeFromDocsToApplyInEDT(null);
      markRemovedCurrentTask(null);
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

  private void markRemovedCurrentTask(@Nullable CommitTask newTask) {
    CommitTask task = currentTask;
    if (task != null && (newTask == null || task.equals(newTask))) {
      task.removed = true;
      cancel("Sync commit intervened");
    }
  }

  private void removeFromDocsToApplyInEDT(@Nullable("null means all") CommitTask newTask) {
    for (int i = documentsToApplyInEDT.size() - 1; i >= 0; i--) {
      CommitTask task = documentsToApplyInEDT.get(i);
      if (newTask == null || task.equals(newTask)) {
        task.removed = true;
        documentsToApplyInEDT.remove(i);
        log("Marked and Removed from EDT apply queue (sync commit called)", task, true);
      }
    }
  }

  private void markRemovedFromDocsToCommit(@Nullable("null means all") final CommitTask newTask) {
    processAll(new Processor<CommitTask>() {
      @Override
      public boolean process(CommitTask task) {
        if (newTask == null || task.equals(newTask)) {
          task.removed = true;
          log("marker as Removed in background queue", task, true);
        }
        return true;
      }
    });
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
    boolean success = false;
    Document document = null;
    Project project = null;
    CommitTask task = null;
    try {
      ProgressIndicator indicator;
      synchronized (documentsToCommit) {
        if (!myEnabled || documentsToCommit.isEmpty()) {
          return false;
        }
        task = documentsToCommit.pullFirst();
        document = task.document;
        indicator = task.indicator;
        project = task.project;

        log("Pulled", task, false, indicator);

        if (project.isDisposed() || !((PsiDocumentManagerBase)PsiDocumentManager.getInstance(project)).isInUncommittedSet(document)) {
          log("Abandon and proceed to next",task, false);
          return true;
        }

        if (task.removed) {
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
        log("commit returned", task, false, finishRunnable, indicator);
      }

      if (success) {
        assert !myApplication.isDispatchThread();
        myApplication.invokeLater(finishRunnable, task.myCreationModalityState);
        log("Invoked later finishRunnable", task, false, finishRunnable, indicator);
      }
    }
    catch (ProcessCanceledException e) {
      cancel(e); // leave queue unchanged
      log("PCE", task, false, e);
      success = false;
    }
    catch (Throwable e) {
      LOG.error(e);
      cancel(e);
    }
    synchronized (documentsToCommit) {
      if (!success && !task.removed) { // sync commit has not intervened
        // reset status for queue back successfully
        doQueue(project, document, "re-added on failure", task.myCreationModalityState);
      }
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

    ProgressIndicator indicator = createProgressIndicator();
    CommitTask task = new CommitTask(document, project, indicator, "Sync commit", ModalityState.current());
    synchronized (documentsToCommit) {
      markRemovedFromDocsToCommit(task);
      markRemovedCurrentTask(task);
      removeFromDocsToApplyInEDT(task);
    }

    log("About to commit sync", task, true, indicator);

    Runnable finish = commitUnderProgress(task, true);
    log("Committed sync", task, true, finish, indicator);
    assert finish != null;

    finish.run();

    // let our thread know that queue must be polled again
    wakeUpQueue();
  }

  @NotNull
  @Override
  protected ProgressIndicator createProgressIndicator() {
    return new StandardProgressIndicatorBase();
  }

  private void startNewTask(@Nullable CommitTask task, @NotNull Object reason) {
    synchronized (documentsToCommit) { // sync to prevent overwriting
      CommitTask cur = currentTask;
      if (cur != null) {
        cur.indicator.cancel();
      }
      currentTask = task;
    }
    log("new task started", task, false, reason);
  }

  // returns finish commit Runnable (to be invoked later in EDT), or null on failure
  @Nullable
  private Runnable commitUnderProgress(@NotNull final CommitTask task, final boolean synchronously) {
    final Project project = task.project;
    final Document document = task.document;
    final List<Processor<Document>> finishProcessors = new SmartList<Processor<Document>>();
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        myApplication.assertReadAccessAllowed();
        if (project.isDisposed()) return;

        final PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);
        if (documentManager.isCommitted(document)) return;

        FileViewProvider viewProvider = documentManager.getCachedViewProvider(document);
        if (viewProvider == null) {
          finishProcessors.add(handleCommitWithoutPsi(documentManager, document, task, synchronously));
          return;
        }

        List<PsiFile> psiFiles = viewProvider.getAllFiles();
        for (PsiFile file : psiFiles) {
          if (file.isValid()) {
            Processor<Document> finishProcessor = doCommit(task, file, synchronously);
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
      log("Could not start read action", task, synchronously, myApplication.isReadAccessAllowed(), Thread.currentThread());
      return null;
    }

    boolean canceled = task.indicator.isCanceled();
    assert !synchronously || !canceled;
    if (canceled || task.removed) {
      return null;
    }

    return new Runnable() {
      @Override
      public void run() {
        myApplication.assertIsDispatchThread();

        Project project = task.project;
        if (project.isDisposed()) return;

        Document document = task.document;

        synchronized (documentsToCommit) {
          boolean isValid = !task.removed;
          for (int i = documentsToApplyInEDT.size() - 1; i >= 0; i--) {
            CommitTask queuedTask = documentsToApplyInEDT.get(i);
            boolean taskIsValid = !queuedTask.removed;
            if (task == queuedTask) { // find the same task in the queue
              documentsToApplyInEDT.remove(i);
              isValid &= taskIsValid;
              log("Task matched, removed from documentsToApplyInEDT", queuedTask, false, task);
            }
            else if (!taskIsValid) {
              documentsToApplyInEDT.remove(i);
              log("Task invalid, removed from documentsToApplyInEDT", queuedTask, false);
            }
          }
          if (!isValid) {
            log("Marked as already committed in EDT apply queue, return", task, true);
            return;
          }
        }

        PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);

        log("Executing later finishCommit", task, false);
        boolean success = documentManager.finishCommit(document, finishProcessors, synchronously, task.reason);
        if (synchronously) {
          assert success;
        }
        log("after call finishCommit",task, synchronously, success);
        if (synchronously || success) {
          assert !documentManager.isInUncommittedSet(document);
        }
        if (!success) {
          // add document back to the queue
          queueCommit(project, document, "Re-added back", task.myCreationModalityState);
        }
      }
    };
  }

  @NotNull
  private Processor<Document> handleCommitWithoutPsi(@NotNull final PsiDocumentManagerBase documentManager,
                                                     @NotNull Document document,
                                                     @NotNull final CommitTask task,
                                                     final boolean synchronously) {
    final long startDocModificationTimeStamp = document.getModificationStamp();
    return new Processor<Document>() {
      @Override
      public boolean process(Document document) {
        log("Finishing without PSI", task, synchronously, document.getModificationStamp(), startDocModificationTimeStamp);
        if (document.getModificationStamp() != startDocModificationTimeStamp ||
            documentManager.getCachedViewProvider(document) != null) {
          return false;
        }

        documentManager.handleCommitWithoutPsi(document);
        return true;
      }
    };
  }

  private boolean processAll(final Processor<CommitTask> processor) {
    final boolean[] result = {true};
    synchronized (documentsToCommit) {
      documentsToCommit.process(new Processor<CommitTask>() {
        @Override
        public boolean process(CommitTask commitTask) {
          result[0] &= processor.process(commitTask);
          return true;
        }
      });
    }
    return result[0];
  }

  @TestOnly
  boolean isEnabled() {
    return myEnabled;
  }

  @Override
  public String toString() {
    return "Document commit thread; application: "+myApplication+"; isDisposed: "+isDisposed+"; myEnabled: "+myEnabled+"; runningWriteActions: "+runningWriteActions;
  }
}
