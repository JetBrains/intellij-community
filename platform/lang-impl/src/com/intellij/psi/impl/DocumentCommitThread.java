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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLock;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.text.DiffLog;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.Queue;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: cdr
 */
public class DocumentCommitThread implements Runnable, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.DocumentCommitThread");
  private static final Key<CommitStage> COMMIT_STAGE = new Key<CommitStage>("Commit stage");

  private final Queue<CommitTask> documentsToCommit = new Queue<CommitTask>(10);
  private volatile boolean isDisposed;
  private ProgressIndicator myProgressIndicator; // guarded by documentsToCommit
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
    stopThread();
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

  private void stopThread() {
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

  private void cancel(@NonNls Object reason) {
    log("Canceled", null, false, myProgressIndicator, "Reason: ", reason);

    useIndicator(null);
  }

  public boolean queueCommit(@NonNls String reason, @NotNull Document document, @NotNull Project project) {
    assert !isDisposed;
    assert project.isInitialized();
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getCachedPsiFile(document);
    if (psiFile == null) return false;

    doQueue(document, project, getCommitStage(document), reason);
    return true;
  }

  private void doQueue(@NotNull Document document, @NotNull Project project, CommitStage start, @NonNls String reason) {
    synchronized (documentsToCommit) {
      if (!changeCommitStage(document, start, CommitStage.QUEUED_TO_COMMIT, false)) return;

      Object[] documentTasks = documentsToCommit.toArray();
      for (Object o : documentTasks) {
        CommitTask task = (CommitTask)o;
        if (task.document == document) {
          ProgressIndicator current = document.getUserData(COMMIT_PROGRESS);
          if (current == null) {
            // already queued, not started yet
          }
          else {
            current.cancel(); // will be re-queued
          }
          return;
        }
      }
      ProgressIndicator indicator = new ProgressIndicatorBase();
      indicator.start();
      documentsToCommit.addLast(new CommitTask(document, project, indicator));
      log("Queued", document, false, reason);
    }
  }

  private final StringBuffer log = new StringBuffer();
  private void log(@NonNls String msg, Document document, boolean synchronously, @NonNls Object... args) {
    //if (true) return;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      String s = msg + (synchronously ? " (sync)" : "") +
                 (document == null ? "" : "; Document: " + System.identityHashCode(document) +
                                          "; stage: " + getCommitStage(document))
                 + "; my indic="+myProgressIndicator;

      for (Object arg : args) {
        s += "; "+arg;
      }
      //System.out.println(s);
      synchronized (log) {
        log.append(s).append("\n");
        if (log.length() > 1000000) {
          log.delete(0, 1000000);
        }
      }
    }
  }

  @TestOnly
  public void printLog() {
    System.err.println(log);
  }
  private void clearLog() {
    log.setLength(0);
  }

  @TestOnly
  public void clearQueue() {
    synchronized (documentsToCommit) {
      documentsToCommit.clear();
    }
    clearLog();
    disable("end of test");
  }

  private static class CommitTask {
    private final Document document;
    private final Project project;
    private final ProgressIndicator indicator; // progress to commit this doc under.
    // running = false means document was removed from the queue, should ignore.
    // canceled = true means commit was canceled, should reschedule for later.

    private CommitTask(@NotNull Document document, @NotNull Project project, @NotNull ProgressIndicator indicator) {
      this.document = document;
      this.project = project;
      this.indicator = indicator;
    }
  }

  private static final Key<ProgressIndicator> COMMIT_PROGRESS = Key.create("COMMIT_PROGRESS");
  private void removeCommitFromQueue(@NotNull Document document) {
    synchronized (documentsToCommit) {
      ProgressIndicator indicator = document.getUserData(COMMIT_PROGRESS);

      if (indicator != null && indicator.isRunning()) {
        indicator.stop(); // mark document as removed

        log("Removed from queue", document, false);
      }
      // let our thread know that queue must be polled again
      wakeUpQueue();
    }
  }

  @Override
  public void run() {
    threadFinished = false;
    while (!isDisposed) {
      try {
        boolean success = false;
        Document document = null;
        Project project = null;
        ProgressIndicator indicator = null;
        try {
          synchronized (documentsToCommit) {
            if (!myEnabled || documentsToCommit.isEmpty()) {
              documentsToCommit.wait();
              continue;
            }
            CommitTask task = documentsToCommit.pullFirst();
            document = task.document;
            indicator = task.indicator;
            project = task.project;

            log("Pulled", document, false, indicator);

            if (getCommitStage(document) != CommitStage.QUEUED_TO_COMMIT
                || project.isDisposed() || !ArrayUtil.contains(document,PsiDocumentManager.getInstance(project).getUncommittedDocuments())) {
              continue;
            }
            if (indicator.isRunning()) {
              useIndicator(indicator);
              document.putUserData(COMMIT_PROGRESS, indicator);
            }
            else {
              success = true; // document has been marked as removed, e.g. by synchronous commit
            }
          }

          Runnable finishRunnable = null;
          if (!success && !indicator.isCanceled()) {
            try {
              finishRunnable = commit(document, project, null, indicator, false);
              success = finishRunnable != null;
              log("Committed", document, false, finishRunnable, indicator);
            }
            finally {
              document.putUserData(COMMIT_PROGRESS, null);
            }
          }

          synchronized (documentsToCommit) {
            if (indicator.isCanceled()) {
              success = false;
            }
            if (success) {
              UIUtil.invokeLaterIfNeeded(finishRunnable);
            }

            log("Invoked later", document, false, success, finishRunnable, indicator);
          }
        }
        catch (ProcessCanceledException ignored) {
          int i = 0; // leave queue unchanged
          cancel(ignored);
          log("PCE", document, false, ignored);
          success = false;
        }
        catch (InterruptedException ignored) {
          // app must be closing
          int i = 0;
          log("IE", document, false, ignored);
          cancel(ignored);
        }
        catch (Throwable e) {
          LOG.error(e);
          cancel(e);
        }
        if (!success && indicator.isRunning()) { // running means sync commit has not intervened
          doQueue(document, project, CommitStage.QUEUED_TO_COMMIT, "re-added on failure");
        }
      }
      catch(Throwable e) {
        e.printStackTrace();
        //LOG.error(e);
      }
    }
    threadFinished = true;
    // ping the thread waiting for close
    wakeUpQueue();
    log("Good bye", null, false);
  }

  public void commitSynchronously(@NotNull Document document, @NotNull Project project, PsiFile excludeFile) {
    assert !isDisposed;
    assert project.isInitialized();
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    setCommitStage(document, CommitStage.ABOUT_TO_BE_SYNC_COMMITTED, true);
    removeCommitFromQueue(document);

    ProgressIndicatorBase indicator = new ProgressIndicatorBase();
    indicator.start();
    log("About to commit sync", document, true, indicator);
    Runnable finish = commit(document, project, excludeFile, indicator, true);
    log("Committed sync", document, true, finish, indicator);

    assert finish != null;
    finish.run();
  }

  private Runnable commit(@NotNull final Document document,
                          @NotNull final Project project,
                          final PsiFile excludeFile,
                          @NotNull final ProgressIndicator indicator,
                          final boolean synchronously) {
    final Runnable[] success = new Runnable[1];
    ((ProgressManagerImpl)ProgressManager.getInstance()).executeProcessUnderProgress(new Runnable() {
      @Override
      public void run() {
        success[0] = commitUnderProgress(document, project, excludeFile, indicator, synchronously);
      }
    }, indicator);
    return success[0];
  }

  private void useIndicator(ProgressIndicator indicator) {
    synchronized (documentsToCommit) { // sync to prevent overwriting indicator
      assert indicator == null || myProgressIndicator != indicator;
      if (myProgressIndicator != null) {
        myProgressIndicator.cancel();
      }
      myProgressIndicator = indicator;
    }
  }

  // returns finish commit Runnable (to be invoked later in EDT), or null on failure
  private Runnable commitUnderProgress(@NotNull final Document document,
                                      @NotNull final Project project,
                                      final PsiFile excludeFile,
                                      @NotNull final ProgressIndicator indicator,
                                      final boolean synchronously) {
    final PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(project);
    final List<Processor<Document>> finishRunnables = new ArrayList<Processor<Document>>();
    Runnable runnable = new Runnable() {
      public void run() {
        final FileViewProvider viewProvider = documentManager.getCachedViewProvider(document);
        if (viewProvider == null) return;
        final List<PsiFile> psiFiles = viewProvider.getAllFiles();
        for (PsiFile file : psiFiles) {
          if (file.isValid() && file != excludeFile) {
            Processor<Document> finishRunnable = doCommit(document, file, indicator, synchronously);
            if (finishRunnable != null) {
              finishRunnables.add(finishRunnable);
            }
          }
        }
      }
    };
    if (synchronously) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      runnable.run();
    }
    else {
      if (!ApplicationManagerEx.getApplicationEx().tryRunReadAction(runnable)) {
        log("Could not start readaction", document, synchronously, ApplicationManager.getApplication().isReadAccessAllowed(), Thread.currentThread());
        return null;
      }
    }

    boolean canceled = indicator.isCanceled();
    if (synchronously) {
      assert !canceled;
    }
    if (canceled || !indicator.isRunning()) {
      assert !synchronously;
      return null;
    }
    if (!synchronously && !changeCommitStage(document, CommitStage.QUEUED_TO_COMMIT, CommitStage.WAITING_FOR_PSI_APPLY, synchronously)) {
      return null;
    }

    Runnable finishRunnable = new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed()) return;
        ApplicationManager.getApplication().runWriteAction(new CommitToPsiFileAction(document, project) {
          public void run() {
            if (project.isDisposed()) return;
            CommitStage stage = getCommitStage(document);
            log("Finish", document, synchronously, project);
            if (stage != (synchronously ? CommitStage.ABOUT_TO_BE_SYNC_COMMITTED : CommitStage.WAITING_FOR_PSI_APPLY)) {
              return; // there must be a synchronous commit sneaked in between queued commit and finish commit, or just document changed meanwhile
            }

            boolean success = false;
            try {
              success = documentManager.finishCommit(document, finishRunnables, synchronously);
              log("Finished", document, synchronously, success, Arrays.asList(documentManager.getUncommittedDocuments()));
              if (synchronously) {
                assert success;
              }
            }
            finally {
              if (success) {
                CommitStage before = getCommitStage(document);
                boolean changed = synchronously || changeCommitStage(document, CommitStage.WAITING_FOR_PSI_APPLY, CommitStage.COMMITTED, false);
                assert changed : "Before: " + before + "; after: " + getCommitStage(document);
              }
            }
            if (synchronously) {
              List<Document> unc = Arrays.asList(documentManager.getUncommittedDocuments());
              assert !unc.contains(document) : unc;
            }
            if (!success) {
              // add document back to the queue
              log("Re-adding back", document, synchronously, success, documentManager.isCommitted(document), Arrays.asList(documentManager.getUncommittedDocuments()));
              queueCommit("Re-added back", document, project);
            }
          }
        });
      }
    };
    return finishRunnable;
  }

  @Nullable("returns runnable to execute under write action in AWT to finish the commit")
  private Processor<Document> doCommit(@NotNull final Document document,
                                              @NotNull final PsiFile file,
                                              @NotNull ProgressIndicator indicator,
                                              final boolean synchronously) {
    ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(file.getProject())).clearTreeHardRef(document);
    final TextBlock textBlock = PsiDocumentManagerImpl.getTextBlock(file);
    if (textBlock.isEmpty()) return null;
    final long startPsiModificationTimeStamp = file.getModificationStamp();
    final long startDocModificationTimeStamp = document.getModificationStamp();
    final FileElement myTreeElementBeingReparsedSoItWontBeCollected = ((PsiFileImpl)file).calcTreeElement();
    if (textBlock.isEmpty()) return null; // if tree was just loaded above textBlock will be cleared by contentsLoaded
    final CharSequence chars = document.getCharsSequence();
    final Boolean data = document.getUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY);
    if (data != null) {
      document.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null);
      file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, data);
    }
    final String oldPsiText =
      ApplicationManagerEx.getApplicationEx().isInternal() && !ApplicationManagerEx.getApplicationEx().isUnitTestMode()
      ? myTreeElementBeingReparsedSoItWontBeCollected.getText()
      : null;
    int startOffset;
    int endOffset;
    int lengthShift;
    if (file.getViewProvider().supportsIncrementalReparse(file.getLanguage())) {
      startOffset = textBlock.getStartOffset();
      int psiEndOffset = textBlock.getPsiEndOffset();
      endOffset = psiEndOffset;
      lengthShift = textBlock.getTextEndOffset() - psiEndOffset;
    }
    else {
      startOffset = 0;
      endOffset = document.getTextLength();
      lengthShift = document.getTextLength() - myTreeElementBeingReparsedSoItWontBeCollected.getTextLength();
    }
    assertBeforeCommit(document, file, textBlock, chars, oldPsiText, myTreeElementBeingReparsedSoItWontBeCollected);
    BlockSupport blockSupport = BlockSupport.getInstance(file.getProject());
    final DiffLog diffLog = blockSupport.reparseRange(file, startOffset, endOffset, lengthShift, chars, indicator);

    return new Processor<Document>() {
      @Override
      public boolean process(Document document) {
        ApplicationManager.getApplication().assertWriteAccessAllowed();
        log("Finishing", document, synchronously, document.getModificationStamp(), startDocModificationTimeStamp);
        //if (file.getModificationStamp() != startPsiModificationTimeStamp) return; // optimistic locking failed
        if (document.getModificationStamp() != startDocModificationTimeStamp) {
          return false; // optimistic locking failed
        }

        try {
          textBlock.performAtomically(new Runnable() {
            @Override
            public void run() {
              file.getManager().performActionWithFormatterDisabled(new Runnable() {
                @Override
                public void run() {
                  synchronized (PsiLock.LOCK) {
                    diffLog.doActualPsiChange(file);
                  }
                }
              });
            }
          });

          assertAfterCommit(document, file, oldPsiText, myTreeElementBeingReparsedSoItWontBeCollected);
        }
        finally {
          textBlock.clear();
          SmartPointerManagerImpl.synchronizePointers(file);
        }

        return true;
      }
    };
  }


  private static void assertBeforeCommit(Document document,
                                         PsiFile file,
                                         TextBlock textBlock,
                                         CharSequence chars,
                                         String oldPsiText,
                                         FileElement myTreeElementBeingReparsedSoItWontBeCollected) {
    int startOffset = textBlock.getStartOffset();
    int psiEndOffset = textBlock.getPsiEndOffset();
    if (oldPsiText != null) {
      @NonNls String msg = "PSI/document inconsistency before reparse: ";
      if (startOffset >= oldPsiText.length()) {
        msg += "startOffset=" + oldPsiText + " while text length is " + oldPsiText.length() + "; ";
        startOffset = oldPsiText.length();
      }

      String psiPrefix = oldPsiText.substring(0, startOffset);
      String docPrefix = chars.subSequence(0, startOffset).toString();
      String psiSuffix = oldPsiText.substring(psiEndOffset);
      String docSuffix = chars.subSequence(textBlock.getTextEndOffset(), chars.length()).toString();
      if (!psiPrefix.equals(docPrefix) || !psiSuffix.equals(docSuffix)) {
        if (!psiPrefix.equals(docPrefix)) {
          msg = msg + "psiPrefix=" + psiPrefix + "; docPrefix=" + docPrefix + ";";
        }
        if (!psiSuffix.equals(docSuffix)) {
          msg = msg + "psiSuffix=" + psiSuffix + "; docSuffix=" + docSuffix + ";";
        }
        throw new AssertionError(msg);
      }
    }
    else if (document.getTextLength() - textBlock.getTextEndOffset() !=
             myTreeElementBeingReparsedSoItWontBeCollected.getTextLength() - psiEndOffset) {
      throw new AssertionError("PSI/document inconsistency before reparse: file=" + file);
    }
  }

  private static void assertAfterCommit(Document document,
                                        final PsiFile file,
                                        String oldPsiText,
                                        FileElement myTreeElementBeingReparsedSoItWontBeCollected) {
    if (myTreeElementBeingReparsedSoItWontBeCollected.getTextLength() != document.getTextLength()) {
      final String documentText = document.getText();
      if (ApplicationManagerEx.getApplicationEx().isInternal()) {
        String fileText = file.getText();
        LOG.error("commitDocument left PSI inconsistent; file len=" + myTreeElementBeingReparsedSoItWontBeCollected.getTextLength() +
                  "; doc len=" + document.getTextLength() +
                  "; doc.getText() == file.getText(): " + Comparing.equal(fileText, documentText) +
                  ";\n file psi text=" + fileText +
                  ";\n doc text=" + documentText +
                  ";\n old psi file text=" + oldPsiText);
      }
      else {
        LOG.error("commitDocument left PSI inconsistent: " + file);
      }

      file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, Boolean.TRUE);
      try {
        BlockSupport blockSupport = BlockSupport.getInstance(file.getProject());
        final DiffLog diffLog = blockSupport.reparseRange(file, 0, documentText.length(), 0, documentText, new ProgressIndicatorBase());
        file.getManager().performActionWithFormatterDisabled(new Runnable() {
          @Override
          public void run() {
            synchronized (PsiLock.LOCK) {
              diffLog.doActualPsiChange(file);
            }
          }
        });

        if (myTreeElementBeingReparsedSoItWontBeCollected.getTextLength() != document.getTextLength()) {
          LOG.error("PSI is broken beyond repair in: " + file);
        }
      }
      finally {
        file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null);
      }
    }
  }

  @NotNull
  static CommitStage getCommitStage(@NotNull Document doc) {
    CommitStage stage = doc.getUserData(COMMIT_STAGE);
    if (stage == null) {
      stage = ((UserDataHolderEx)doc).putUserDataIfAbsent(COMMIT_STAGE, CommitStage.DIRTY);
    }
    return stage;
  }

  private void setCommitStage(@NotNull Document document, @NotNull CommitStage stage, boolean synchronously) {
    document.putUserData(COMMIT_STAGE, stage);
    log("Set stage", document, synchronously);
  }

  private boolean changeCommitStage(@NotNull Document document,
                                           CommitStage expected,
                                           @NotNull CommitStage stage,
                                           boolean synchronously) {
    boolean replaced = ((UserDataHolderEx)document).replace(COMMIT_STAGE, expected, stage);
    log("Changed stage", document, synchronously, expected, stage, replaced);
    return replaced;
  }

  private static enum CommitStage {
    DIRTY, QUEUED_TO_COMMIT, WAITING_FOR_PSI_APPLY, COMMITTED, ABOUT_TO_BE_SYNC_COMMITTED
  }
}
