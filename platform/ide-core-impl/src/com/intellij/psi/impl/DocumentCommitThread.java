// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.ObjectUtilsRt;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.AppJavaExecutorUtil;
import com.intellij.util.concurrency.CoroutineDispatcherBackedExecutor;
import com.intellij.util.ui.EDT;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@ApiStatus.Internal
public final class DocumentCommitThread implements Disposable, DocumentCommitProcessor {
  private static final Logger LOG = Logger.getInstance(DocumentCommitThread.class);

  private final CoroutineDispatcherBackedExecutor executor;
  private volatile boolean isDisposed;

  static DocumentCommitThread getInstance() {
    return (DocumentCommitThread)ApplicationManager.getApplication().getService(DocumentCommitProcessor.class);
  }

  DocumentCommitThread(@NotNull CoroutineScope coroutineScope) {
    executor = AppJavaExecutorUtil.createBoundedTaskExecutor("Document Commit Pool", coroutineScope);
  }

  @Override
  public void dispose() {
    isDisposed = true;
  }

  @Override
  public void commitAsynchronously(@NotNull Project project,
                                   @NotNull PsiDocumentManagerBase documentManager,
                                   @NotNull Document document,
                                   @NonNls @NotNull Object reason,
                                   @NotNull ModalityState modality,
                                   @NotNull FileViewProvider cachedViewProvider) {
    assert !isDisposed : "already disposed";
    if (!project.isInitialized()) return;
    if (documentManager.myProject != project) {
      throw new IllegalArgumentException("Wrong project: "+project+"; expected: "+documentManager.myProject);
    }

    assert cachedViewProvider.isEventSystemEnabled() : "Asynchronous commit is only supported for physical PSI" +
                                                       ", document=" + document +
                                                       ", cachedViewProvider=" + cachedViewProvider +" ("+cachedViewProvider.getClass()+")";
    TransactionGuard.getInstance().assertWriteSafeContext(modality);

    CommitTask task = new CommitTask(project, document, reason, modality, documentManager.getLastCommittedText(document), cachedViewProvider);
    ReadAction
      .nonBlocking(() -> commitUnderProgress(task, false, documentManager))
      .expireWhen(() -> project.isDisposed() || isDisposed || !documentManager.isInUncommittedSet(document) || !task.isStillValid())
      .coalesceBy(task)
      .finishOnUiThread(modality, Runnable::run)
      .submit(executor);
  }

  @Override
  public void commitSynchronously(@NotNull Document document, @NotNull Project project, @NotNull PsiFile psiFile) {
    assert !isDisposed;

    if (!project.isInitialized() && !project.isDefault()) {
      throw new IllegalArgumentException("Must not call sync commit with unopened project: "+ project + "; Disposed: " + project.isDisposed() + "; Open: " + project.isOpen());
    }

    PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);
    CommitTask task = new CommitTask(project, document, "Sync commit", ModalityState.defaultModalityState(),
                                     documentManager.getLastCommittedText(document), psiFile.getViewProvider());

    commitUnderProgress(task, true, documentManager).run();
  }

  // returns finish commit Runnable (to be invoked later in EDT) or null on failure
  private @NotNull Runnable commitUnderProgress(@NotNull CommitTask task, boolean synchronously, @NotNull PsiDocumentManagerBase documentManager) {
    Document document = task.document;
    Project project = task.project;
    List<BooleanRunnable> finishProcessors = new SmartList<>();
    List<BooleanRunnable> reparseInjectedProcessors = new SmartList<>();

    FileViewProvider viewProvider = documentManager.getCachedViewProvider(document);
    if (viewProvider == null) {
      finishProcessors.add(handleCommitWithoutPsi(task, documentManager));
    }
    else {
      // while we were messing around transferring things to background thread, the ViewProvider can become obsolete
      // when e.g., virtual file was renamed.
      // store new provider to retain it from GC
      task.cachedViewProvider = viewProvider;

      for (PsiFile file : viewProvider.getAllFiles()) {
        FileASTNode oldFileNode = file.getNode();
        if (oldFileNode == null) {
          throw new AssertionError("No node for " + file.getClass() + " in " + file.getViewProvider().getClass() +
                                   " of size " + StringUtil.formatFileSize(document.getTextLength()) +
                                   " (is too large = " + SingleRootFileViewProvider
                                     .isTooLargeForIntelligence(viewProvider.getVirtualFile(), (long)document.getTextLength()) + ")");
        }
        ProperTextRange changedPsiRange = ChangedPsiRangeUtil
          .getChangedPsiRange(file, document, task.myLastCommittedText, document.getImmutableCharSequence());
        if (changedPsiRange != null) {
          BooleanRunnable finishProcessor = doCommit(task, file, oldFileNode, changedPsiRange, reparseInjectedProcessors, documentManager);
          finishProcessors.add(finishProcessor);
        }
      }
    }

    return () -> {
      if (project.isDisposed()) {
        return;
      }
      boolean success = documentManager.finishCommit(document, finishProcessors, reparseInjectedProcessors, synchronously, task.reason);
      if (synchronously) {
        assert success;
      }
      if (synchronously || success) {
        assert !documentManager.isInUncommittedSet(document);
      }
      if (!success && viewProvider != null && viewProvider.isEventSystemEnabled()) {
        // add a document back to the queue
        commitAsynchronously(project, documentManager, document, "Re-added back", task.myCreationModality, viewProvider);
      }
    };
  }

  private static @NotNull BooleanRunnable handleCommitWithoutPsi(@NotNull CommitTask task,
                                                                 @NotNull PsiDocumentManagerBase documentManager) {
    return () -> {
      if (!task.isStillValid() || documentManager.getCachedViewProvider(task.document) != null) {
        return false;
      }

      documentManager.handleCommitWithoutPsi(task.document);
      return true;
    };
  }

  @Override
  public String toString() {
    return "Document commit thread; application: "+ApplicationManager.getApplication()+"; isDisposed: "+isDisposed;
  }

  @TestOnly
  // NB: failures applying EDT tasks are not handled - i.e., failed documents are added back to the queue and the method returns
  public void waitForAllCommits(long timeout, @NotNull TimeUnit timeUnit) throws ExecutionException, InterruptedException, TimeoutException {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      while (!executor.isEmpty()) {
        executor.waitAllTasksExecuted(timeout, timeUnit);
      }
      return;
    }
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();

    EDT.dispatchAllInvocationEvents();
    while (!executor.isEmpty()) {
      executor.waitAllTasksExecuted(timeout, timeUnit);
      EDT.dispatchAllInvocationEvents();
    }
  }

  private static class CommitTask {
    private final @NotNull Document document;
    final @NotNull Project project;
    private final int modificationSequence; // store initial document modification sequence here to check if it changed later before commit in EDT
    private volatile @NotNull FileViewProvider cachedViewProvider; // to retain viewProvider to avoid surprising getCachedProvider() == null half-way through commit

    final @NotNull Object reason;
    final @NotNull ModalityState myCreationModality;
    private final CharSequence myLastCommittedText;

    CommitTask(@NotNull Project project,
               @NotNull Document document,
               @NotNull @NonNls Object reason,
               @NotNull ModalityState modality,
               @NotNull CharSequence lastCommittedText,
               @NotNull FileViewProvider cachedViewProvider) {
      this.document = document;
      this.project = project;
      this.reason = reason;
      myCreationModality = modality;
      myLastCommittedText = lastCommittedText;
      modificationSequence = ((DocumentEx)document).getModificationSequence();
      this.cachedViewProvider = cachedViewProvider;
    }

    @Override
    public @NonNls String toString() {
      String reasonInfo = " task reason: " + StringUtil.first(String.valueOf(reason), 180, true) +
                          (isStillValid() ? "" : "; changed: old seq=" + modificationSequence + ", new seq=" + ((DocumentEx)document).getModificationSequence());
      String contextInfo = " modality: " + myCreationModality;
      return System.identityHashCode(this)+"; " + contextInfo + reasonInfo;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof CommitTask task)) return false;

      return Comparing.equal(document, task.document) && project.equals(task.project);
    }

    @Override
    public int hashCode() {
      int result = document.hashCode();
      result = 31 * result + project.hashCode();
      return result;
    }

    boolean isStillValid() {
      return ((DocumentEx)document).getModificationSequence() == modificationSequence;
    }
  }

  // returns runnable to execute under the write action in AWT to finish the commit
  private static @NotNull BooleanRunnable doCommit(@NotNull CommitTask task,
                                          @NotNull PsiFile file,
                                          @NotNull FileASTNode oldFileNode,
                                          @NotNull ProperTextRange changedPsiRange,
                                          @NotNull List<? super BooleanRunnable> outReparseInjectedProcessors,
                                          @NotNull PsiDocumentManagerBase documentManager) {
    Document document = task.document;
    CharSequence newDocumentText = document.getImmutableCharSequence();

    Boolean data = document.getUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY);
    if (data != null) {
      document.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null);
      file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, data);
    }

    DiffLog diffLog;
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (indicator == null) indicator = new EmptyProgressIndicator();
    try {
      BlockSupportImpl.ReparseResult result =
        BlockSupportImpl.reparse(file, oldFileNode, changedPsiRange, newDocumentText, indicator, task.myLastCommittedText);
      diffLog = result.log;

      List<BooleanRunnable> injectedRunnables =
        documentManager.reparseChangedInjectedFragments(document, file, changedPsiRange, indicator, result.oldRoot, result.newRoot);
      outReparseInjectedProcessors.addAll(injectedRunnables);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return () -> {
        documentManager.forceReload(file.getViewProvider().getVirtualFile(), file.getViewProvider());
        return true;
      };
    }

    return () -> {
      FileViewProvider viewProvider = file.getViewProvider();
      if (!task.isStillValid() || documentManager.getCachedViewProvider(document) != viewProvider) {
        return false; // optimistic locking failed
      }

      if (!ApplicationManager.getApplication().isWriteAccessAllowed() && documentManager.isEventSystemEnabled(document)) {
        VirtualFile vFile = viewProvider.getVirtualFile();
        LOG.error("Write action expected" +
                  "; document=" + document +
                  "; file=" + file + " of " + file.getClass() +
                  "; file.valid=" + file.isValid() +
                  "; file.eventSystemEnabled=" + viewProvider.isEventSystemEnabled() +
                  "; viewProvider=" + viewProvider + " of " + viewProvider.getClass() +
                  "; language=" + file.getLanguage() +
                  "; vFile=" + vFile + " of " + vFile.getClass() +
                  "; free-threaded=" + AbstractFileViewProvider.isFreeThreaded(viewProvider));
      }

      diffLog.doActualPsiChange(file);

      assertAfterCommit(document, file, oldFileNode);
      ObjectUtilsRt.reachabilityFence(task.cachedViewProvider); // just to make an impression the field is used
      return true;
    };
  }

  private static void assertAfterCommit(@NotNull Document document, @NotNull PsiFile file, @NotNull FileASTNode oldFileNode) {
    if (oldFileNode.getTextLength() != document.getTextLength()) {
      String documentText = document.getText();
      String fileText = file.getText();
      boolean sameText = Objects.equals(fileText, documentText);
      String errorMessage = "commitDocument() left PSI inconsistent: " + DebugUtil.diagnosePsiDocumentInconsistency(file, document) +
                            "; node.length=" + oldFileNode.getTextLength() +
                            "; doc.text" + (sameText ? "==" : "!=") + "file.text" +
                            "; file name:" + file.getName() +
                            "; type:" + file.getFileType() +
                            "; lang:" + file.getLanguage();
      PluginException.logPluginError(LOG, errorMessage, null, file.getLanguage().getClass());

      file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, Boolean.TRUE);
      try {
        BlockSupport blockSupport = BlockSupport.getInstance(file.getProject());
        DiffLog diffLog = blockSupport.reparseRange(file, file.getNode(), new TextRange(0, documentText.length()), documentText,
                                                          new StandardProgressIndicatorBase(),
                                                          oldFileNode.getText());
        diffLog.doActualPsiChange(file);

        if (oldFileNode.getTextLength() != document.getTextLength()) {
          PluginException.logPluginError(LOG, "PSI is broken beyond repair in: " + file, null, file.getLanguage().getClass());
        }
      }
      finally {
        file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null);
      }
    }
  }
}
