/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.Queue;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class DocumentCommitThread extends DocumentCommitProcessor implements Runnable, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.DocumentCommitThread");

  private final Queue<CommitTask> documentsToCommit = new Queue<CommitTask>(10);
  private final List<CommitTask> documentsToApplyInEDT = new ArrayList<CommitTask>(10);  // guarded by documentsToCommit
  private volatile boolean isDisposed;
  private CommitTask currentTask; // guarded by documentsToCommit
  private volatile boolean threadFinished;
  private volatile boolean myEnabled = true; // true if we can do commits. set to false temporarily during the write action.

  public static DocumentCommitThread getInstance() {
    return ServiceManager.getService(DocumentCommitThread.class);
  }

  public DocumentCommitThread() {
    log("Starting thread",null, false);
    new Thread(this, "Document commit thread").start();
  }

  @Override
  public void dispose() {
    isDisposed = true;
    synchronized (documentsToCommit) {
      documentsToCommit.clear();
    }
    cancel("Stop thread");
    wakeUpQueue();
    while (!threadFinished) {
      wakeUpQueue();
      synchronized (documentsToCommit) {
        try {
          documentsToCommit.wait(10);
        }
        catch (InterruptedException ignored) {
        }
      }
    }
  }

  public void disable(@NonNls Object reason) {
    // write action has just started, all commits are useless
    cancel(reason);
    myEnabled = false;
    log("Disabled", null, false, reason);
  }

  public void enable(Object reason) {
    myEnabled = true;
    wakeUpQueue();
    log("Enabled", null, false, reason);
  }

  private void wakeUpQueue() {
    synchronized (documentsToCommit) {
      documentsToCommit.notifyAll();
    }
  }

  private void cancel(@NonNls Object reason) {
    startNewTask(null, reason);
  }

  @Override
  public void commitAsynchronously(@NotNull final Project project, @NotNull final Document document, @NonNls @NotNull Object reason) {
    queueCommit(project, document, reason);
  }

  public void queueCommit(@NotNull final Project project, @NotNull final Document document, @NonNls @NotNull Object reason) {
    assert !isDisposed : "already disposed";

    if (!project.isInitialized()) return;
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getCachedPsiFile(document);
    if (psiFile == null) return;

    doQueue(project, document, reason);
  }

  private void doQueue(Project project, Document document, Object reason) {
    synchronized (documentsToCommit) {
      ProgressIndicator indicator = new DaemonProgressIndicator();
      CommitTask newTask = new CommitTask(document, project, indicator, reason);

      markRemovedFromDocsToCommit(newTask);
      markRemovedCurrentTask(newTask);
      removeFromDocsToApplyInEDT(newTask);

      documentsToCommit.addLast(newTask);
      log("Queued", newTask, false, reason);

      wakeUpQueue();
    }
  }

  private final StringBuilder log = new StringBuilder();

  @Override
  public void log(@NonNls String msg, CommitTask task, boolean synchronously, @NonNls Object... args) {
    if (true) return;

    String indent = new SimpleDateFormat("mm:ss:SSSS").format(new Date()) +
      (SwingUtilities.isEventDispatchThread() ? "-    " : Thread.currentThread().getName().equals("Document commit thread") ? "-  >" : "-");
    @NonNls
    String s = indent +
               msg + (synchronously ? " (sync)" : "") +
               (task == null ? "" : "; task: " + task+" ("+System.identityHashCode(task)+")");

    for (Object arg : args) {
      if (!StringUtil.isEmpty(String.valueOf(arg))) {
        s += "; "+arg;
      }
    }
    if (task != null) {
      boolean stillUncommitted = !task.project.isDisposed() &&
                                 ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(task.project)).isInUncommittedSet(task.document);
      if (stillUncommitted) {
        s += "; Uncommitted: " + task.document;
      }
    }

    System.err.println(s);

    log.append(s).append("\n");
    if (log.length() > 1000000) {
      log.delete(0, 1000000);
    }
  }


  // cancels all pending commits
  @TestOnly
  public void cancelAll() {
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
    log.setLength(0);
    disable("end of test");
    wakeUpQueue();
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
    threadFinished = false;
    try {
      while (!isDisposed) {
        try {
          pollQueue();
        }
        catch(Throwable e) {
          LOG.error(e);
        }
      }
    }
    finally {
      threadFinished = true;
    }
    // ping the thread waiting for close
    wakeUpQueue();
    log("Good bye", null, false);
  }

  private void pollQueue() {
    boolean success = false;
    Document document = null;
    Project project = null;
    CommitTask task = null;
    try {
      ProgressIndicator indicator;
      synchronized (documentsToCommit) {
        if (!myEnabled || documentsToCommit.isEmpty()) {
          documentsToCommit.wait();
          return;
        }
        task = documentsToCommit.pullFirst();
        document = task.document;
        indicator = task.indicator;
        project = task.project;

        log("Pulled", task, false, indicator);

        if (project.isDisposed() || !((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(project)).isInUncommittedSet(document)) {
          log("Abandon and proceed to next",task, false);
          return;
        }

        if (task.removed) {
          return; // document has been marked as removed, e.g. by synchronous commit
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
        assert !ApplicationManager.getApplication().isDispatchThread();
        UIUtil.invokeLaterIfNeeded(finishRunnable);
        log("Invoked later finishRunnable", task, false, success, finishRunnable, indicator);
      }
    }
    catch (ProcessCanceledException e) {
      cancel(e); // leave queue unchanged
      log("PCE", task, false, e);
      success = false;
    }
    catch (InterruptedException e) {
      // app must be closing
      log("IE", task, false, e);
      cancel(e);
    }
    catch (Throwable e) {
      LOG.error(e);
      cancel(e);
    }
    synchronized (documentsToCommit) {
      if (!success && !task.removed) { // sync commit has not intervened
        // reset status for queue back successfully
        doQueue(project, document, "re-added on failure");
      }
      currentTask = null; // do not cancel, it's being invokeLatered
    }
  }

  @Override
  public void commitSynchronously(@NotNull Document document, @NotNull Project project) {
    assert !isDisposed;
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    if (!project.isInitialized() && !project.isDefault()) {
      @NonNls String s = project + "; Disposed: "+project.isDisposed()+"; Open: "+project.isOpen();
      s += "; SA Passed: ";
      try {
        s += ((StartupManagerImpl)StartupManager.getInstance(project)).startupActivityPassed();
      }
      catch (Exception e) {
        s += e;
      }
      try {
        Disposer.dispose(project);
      }
      catch (Throwable ignored) {
        // do not fill log with endless exceptions
      }
      throw new RuntimeException(s);
    }

    ProgressIndicator indicator = createProgressIndicator();
    CommitTask task = new CommitTask(document, project, indicator, "Sync commit");
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
    return new ProgressIndicatorBase();
  }

  private void startNewTask(CommitTask task, Object reason) {
    synchronized (documentsToCommit) { // sync to prevent overwriting
      CommitTask cur = currentTask;
      if (cur != null) {
        cur.indicator.cancel();
      }
      currentTask = task;
    }
  }

  // returns finish commit Runnable (to be invoked later in EDT), or null on failure
  @Nullable
  private Runnable commitUnderProgress(@NotNull final CommitTask task,
                                       final boolean synchronously) {
    final Project project = task.project;
    final Document document = task.document;
    final List<Processor<Document>> finishProcessors = new SmartList<Processor<Document>>();
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().assertReadAccessAllowed();
        if (project.isDisposed()) return;
        final PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(project);
        FileViewProvider viewProvider = documentManager.getCachedViewProvider(document);
        if (viewProvider == null) return;
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
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      runnable.run();
    }
    else {
      if (!ApplicationManagerEx.getApplicationEx().tryRunReadAction(runnable)) {
        log("Could not start read action", task, synchronously, ApplicationManager.getApplication().isReadAccessAllowed(), Thread.currentThread());
        return null;
      }
    }

    boolean canceled = task.indicator.isCanceled();
    assert !synchronously || !canceled;
    if (canceled || task.removed) {
      return null;
    }

    Runnable finishRunnable = new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().assertIsDispatchThread();

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

        PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(project);

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
          queueCommit(project, document, "Re-added back");
        }
      }
    };
    return finishRunnable;
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
}
