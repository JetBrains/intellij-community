/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomManager;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.TreeAspectEvent;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLock;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.text.DiffLog;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DocumentCommitProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.DocumentCommitThread");

  public abstract void commitSynchronously(@NotNull Document document, @NotNull Project project);
  public abstract void commitAsynchronously(@NotNull final Project project, @NotNull final Document document, @NonNls @NotNull Object reason);

  protected static class CommitTask {
    public final Document document;
    public final Project project;

    // when queued it's not started
    // when dequeued it's started
    // when failed it's canceled
    public final ProgressIndicator indicator; // progress to commit this doc under.
    public final Object reason;
    public boolean removed; // task marked as removed, should be ignored.

    public CommitTask(@NotNull Document document,
                       @NotNull Project project,
                       @NotNull ProgressIndicator indicator,
                       @NotNull Object reason) {
      this.document = document;
      this.project = project;
      this.indicator = indicator;
      this.reason = reason;
    }

    @NonNls
    @Override
    public String toString() {
      return "Project: " + project.getName()
             + ", Doc: "+ document +" ("+  StringUtil.first(document.getImmutableCharSequence(), 12, true).toString().replaceAll("\n", " ")+")"
             +(indicator.isCanceled() ? " (Canceled)" : "") + (removed ? "Removed" : "");
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
  }

  @Nullable("returns runnable to execute under write action in AWT to finish the commit")
  public Processor<Document> doCommit(@NotNull final CommitTask task,
                                      @NotNull final PsiFile file,
                                      final boolean synchronously) {
    Document document = task.document;
    final long startDocModificationTimeStamp = document.getModificationStamp();
    final FileElement myTreeElementBeingReparsedSoItWontBeCollected = ((PsiFileImpl)file).calcTreeElement();
    final CharSequence chars = document.getImmutableCharSequence();
    final Boolean data = document.getUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY);
    if (data != null) {
      document.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null);
      file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, data);
    }
    final CharSequence oldPsiText = myTreeElementBeingReparsedSoItWontBeCollected.getChars();
    final TextRange changedPsiRange = getChangedPsiRange(file, oldPsiText, chars);

    BlockSupport blockSupport = BlockSupport.getInstance(file.getProject());
    final DiffLog diffLog = blockSupport.reparseRange(file, changedPsiRange, chars, task.indicator);

    return new Processor<Document>() {
      @Override
      public boolean process(Document document) {
        ApplicationManager.getApplication().assertWriteAccessAllowed();
        log("Finishing", task, synchronously, document.getModificationStamp(), startDocModificationTimeStamp);
        if (document.getModificationStamp() != startDocModificationTimeStamp ||
            ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(file.getProject())).getCachedViewProvider(document) != file.getViewProvider()) {
          return false; // optimistic locking failed
        }

        doActualPsiChange(file, diffLog);

        assertAfterCommit(document, file, oldPsiText, myTreeElementBeingReparsedSoItWontBeCollected);

        return true;
      }
    };
  }

  public static TextRange getChangedPsiRange(@NotNull PsiFile file, @NotNull CharSequence oldPsiText, @NotNull CharSequence newDocumentText) {
    if (!file.getViewProvider().supportsIncrementalReparse(file.getLanguage())) {
      return new TextRange(0, newDocumentText.length());
    }

    int commonPrefixLength = StringUtil.commonPrefixLength(oldPsiText, newDocumentText);
    if (commonPrefixLength == newDocumentText.length() && newDocumentText.length() == oldPsiText.length()) {
      return new TextRange(0, newDocumentText.length());
    }

    int commonSuffixLength = StringUtil.commonSuffixLength(oldPsiText, newDocumentText);
    return new TextRange(commonPrefixLength, Math.max(commonPrefixLength, oldPsiText.length() - commonSuffixLength));
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

  private void assertAfterCommit(@NotNull Document document,
                                 @NotNull final PsiFile file,
                                 @NotNull CharSequence oldPsiText,
                                 @NotNull FileElement myTreeElementBeingReparsedSoItWontBeCollected) {
    if (myTreeElementBeingReparsedSoItWontBeCollected.getTextLength() != document.getTextLength()) {
      final String documentText = document.getText();
      String fileText = file.getText();
      LOG.error("commitDocument left PSI inconsistent: " + file +
                "; file len=" + myTreeElementBeingReparsedSoItWontBeCollected.getTextLength() +
                "; doc len=" + document.getTextLength() +
                "; doc.getText() == file.getText(): " + Comparing.equal(fileText, documentText),
                new Attachment("file psi text", fileText),
                new Attachment("old text", documentText),
                new Attachment("old psi file text", oldPsiText.toString()));

      file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, Boolean.TRUE);
      try {
        BlockSupport blockSupport = BlockSupport.getInstance(file.getProject());
        final DiffLog diffLog = blockSupport.reparseRange(file, new TextRange(0, documentText.length()), documentText, createProgressIndicator());
        doActualPsiChange(file, diffLog);

        if (myTreeElementBeingReparsedSoItWontBeCollected.getTextLength() != document.getTextLength()) {
          LOG.error("PSI is broken beyond repair in: " + file);
        }
      }
      finally {
        file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null);
      }
    }
  }

  public void log(@NonNls String msg, @Nullable CommitTask task, boolean synchronously, @NonNls Object... args) {
  }

  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new EmptyProgressIndicator();
  }
}
