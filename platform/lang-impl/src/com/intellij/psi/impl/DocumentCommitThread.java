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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLock;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.text.DiffLog;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.Processor;
import com.intellij.util.containers.OrderedSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: cdr
 */
public class DocumentCommitThread extends AbstractProjectComponent implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.DocumentCommitThread");

  private final OrderedSet<Document> documentsToCommit = new OrderedSet<Document>();
  private volatile boolean isDisposed;
  public static final Document THE_POISON_PILL = new DocumentImpl(true);
  private volatile DaemonProgressIndicator myProgressIndicator = new DaemonProgressIndicator();
  private volatile boolean threadFinished;

  public static DocumentCommitThread getInstance(Project project) {
    return project.getComponent(DocumentCommitThread.class);
  }

  public DocumentCommitThread(@NotNull final Project project, @NotNull StartupManager startupManager) {
    super(project);
    if (project.isDefault()) return;
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        new Thread(DocumentCommitThread.this, "Document commit thread").start();
        Disposer.register(project, new Disposable() {
          @Override
          public void dispose() {
            stop();
          }
        });
      }
    };
    startupManager.runWhenProjectIsInitialized(runnable);
  }

  private void stop() {
    isDisposed = true;
    cancelAndClearAll();
    boolean added = doQueue(THE_POISON_PILL);
    assert added : documentsToCommit;
    while (!threadFinished) {
      synchronized (documentsToCommit) {
        try {
          documentsToCommit.wait(10);
        }
        catch (InterruptedException ignored) {
        }
      }
    }
  }

  private void cancelAndClearAll() {
    synchronized (documentsToCommit) {
      documentsToCommit.clear();
      // let our thread know that next doc in queue is available
      documentsToCommit.notifyAll();
    }
    recreateIndicator();
  }
  void cancel() {
    recreateIndicator();
  }

  public boolean queueCommit(@NotNull Document document) {
    assert !isDisposed;
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getCachedPsiFile(document);
    if (psiFile == null || !psiFile.isPhysical()) return false;
    if (!doQueue(document)) return false;
    return PsiDocumentManagerImpl.changeCommitStage(document, PsiDocumentManagerImpl.CommitStage.DIRTY, PsiDocumentManagerImpl.CommitStage.QUEUED_TO_COMMIT);
  }

  private boolean doQueue(Document document) {
    boolean added;
    synchronized (documentsToCommit) {
      added = documentsToCommit.add(document);
      // let our thread know that next doc in queue is available
      documentsToCommit.notifyAll();
    }
    return added;
  }

  void cancelCommit(@NotNull Document document) {
    synchronized (documentsToCommit) {
      documentsToCommit.remove(document);
      // let our thread know that queue must be polled again
      documentsToCommit.notifyAll();
    }
    cancelRunningCommitFor(document);
  }

  private void cancelRunningCommitFor(@NotNull Document document) {
    DaemonProgressIndicator indicator = myProgressIndicator;
    Document runningDoc = indicator.getUserData(RUNNING_COMMIT_DOCUMENT);
    if (runningDoc == document) {
      indicator.cancel();
    }
  }

  @Override
  public void run() {
    threadFinished = false;
    try {
      while (!isDisposed) {
        try {
          Document document;
          synchronized (documentsToCommit) {
            int size = documentsToCommit.size();
            if (size == 0) {
              documentsToCommit.wait();
              continue;
            }
            document = documentsToCommit.get(size - 1);
          }
          if (document == THE_POISON_PILL) break;
          boolean success = commit(myProject, document, null, recreateIndicator(), false);

          // ping the thread waiting for close
          synchronized (documentsToCommit) {
            if (success) {
              documentsToCommit.remove(document);
            }
            documentsToCommit.notifyAll();
          }
        }
        catch (ProcessCanceledException ignored) {
        }
        catch (InterruptedException ignored) {
          // app must be closing
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
    finally {
      threadFinished = true;
    }
  }

  public boolean commitSynchronously(Project project, @NotNull Document document, PsiFile excludeFile) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    cancelCommit(document);
    PsiDocumentManagerImpl.setCommitStage(document, PsiDocumentManagerImpl.CommitStage.QUEUED_TO_COMMIT);
    boolean success = commit(project, document, excludeFile, new DaemonProgressIndicator(), true);
    assert success;
    return success;
  }

  private static final Key<Document> RUNNING_COMMIT_DOCUMENT = Key.create("RUNNING_COMMIT_DOCUMENT");
  private boolean commit(@NotNull final Project project,
                         @NotNull final Document document,
                         final PsiFile excludeFile,
                         @NotNull final DaemonProgressIndicator indicator,
                         final boolean synchronously) {
    long start = System.currentTimeMillis();
    indicator.putUserData(RUNNING_COMMIT_DOCUMENT, document);
    try {
      final boolean[] success = new boolean[1];
      ((ProgressManagerImpl)ProgressManager.getInstance()).executeProcessUnderProgress(new Runnable() {
        @Override
        public void run() {
          success[0] = commitUnderProgress(project, document, excludeFile, indicator, synchronously);
        }
      }, indicator);
      return success[0];
    }
    finally {
      long finish = System.currentTimeMillis();
      indicator.putUserData(RUNNING_COMMIT_DOCUMENT, null);
    }
  }

  private DaemonProgressIndicator recreateIndicator() {
    myProgressIndicator.cancel();
    DaemonProgressIndicator indicator = new DaemonProgressIndicator();
    myProgressIndicator = indicator;
    return indicator;
  }

  private boolean commitUnderProgress(@NotNull Project project,
                                      @NotNull final Document document,
                                      final PsiFile excludeFile,
                                      @NotNull final DaemonProgressIndicator indicator,
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
            Processor<Document> finishRunnable = doCommit(document, file, indicator);
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
      if (!ApplicationManagerEx.getApplicationEx().tryRunReadAction(runnable)) return false;
    }

    boolean canceled = indicator.isCanceled();
    if (synchronously) {
      assert !canceled;
    }
    if (!canceled) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          boolean success = documentManager.finishCommit(document, finishRunnables, synchronously);
          if (synchronously) {
            assert success;
          }
          if (!success && !documentManager.isCommitted(document)) {
            // add document back to the queue
            doQueue(document);
          }
        }
      });
    }
    return true;
  }

  @Nullable("returns runnable to execute under write action in AWT to finish the commit")
  private static Processor<Document> doCommit(@NotNull final Document document, @NotNull final PsiFile file, @NotNull DaemonProgressIndicator indicator) {
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
      String msg = "PSI/document inconsistency before reparse: ";
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
        final DiffLog diffLog = blockSupport.reparseRange(file, 0, documentText.length(), 0, documentText, new DaemonProgressIndicator());
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
}
