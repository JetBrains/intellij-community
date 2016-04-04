/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
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
import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DocumentCommitProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.DocumentCommitThread");

  public abstract void commitSynchronously(@NotNull Document document, @NotNull Project project);
  public abstract void commitAsynchronously(@NotNull final Project project,
                                            @NotNull final Document document,
                                            @NonNls @NotNull Object reason,
                                            @NotNull ModalityState currentModalityState);

  protected static class CommitTask {
    @NotNull final Document document;
    @NotNull final Project project;

    // when queued it's not started
    // when dequeued it's started
    // when failed it's canceled
    @NotNull final ProgressIndicator indicator; // progress to commit this doc under.
    @NotNull final Object reason;
    @NotNull final ModalityState myCreationModalityState;
    private final CharSequence myLastCommittedText;
    public boolean removed; // task marked as removed should be ignored.

    protected CommitTask(@NotNull Document document,
                      @NotNull Project project,
                      @NotNull ProgressIndicator indicator,
                      @NotNull Object reason,
                      @NotNull ModalityState currentModalityState) {
      this.document = document;
      this.project = project;
      this.indicator = indicator;
      this.reason = reason;
      myCreationModalityState = currentModalityState;
      myLastCommittedText = PsiDocumentManager.getInstance(project).getLastCommittedText(document);
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

  // public for Upsource
  @Nullable("returns runnable to execute under write action in AWT to finish the commit")
  public Processor<Document> doCommit(@NotNull final CommitTask task,
                                      @NotNull final PsiFile file,
                                      final boolean synchronously) {
    Document document = task.document;
    final long startDocModificationTimeStamp = document.getModificationStamp();
    final FileElement myTreeElementBeingReparsedSoItWontBeCollected = ((PsiFileImpl)file).calcTreeElement();
    final CharSequence chars = document.getImmutableCharSequence();
    final TextRange changedPsiRange = getChangedPsiRange(file, myTreeElementBeingReparsedSoItWontBeCollected, chars);
    if (changedPsiRange == null) {
      return null;
    }

    final Boolean data = document.getUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY);
    if (data != null) {
      document.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null);
      file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, data);
    }

    BlockSupport blockSupport = BlockSupport.getInstance(file.getProject());
    final DiffLog diffLog = blockSupport.reparseRange(file, changedPsiRange, chars, task.indicator, task.myLastCommittedText);

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

        assertAfterCommit(document, file, myTreeElementBeingReparsedSoItWontBeCollected);

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
          patternIndex += (fromStart ? matchingLength : -matchingLength);
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
                                 @NotNull FileElement myTreeElementBeingReparsedSoItWontBeCollected) {
    if (myTreeElementBeingReparsedSoItWontBeCollected.getTextLength() != document.getTextLength()) {
      final String documentText = document.getText();
      String fileText = file.getText();
      LOG.error("commitDocument left PSI inconsistent: " + DebugUtil.diagnosePsiDocumentInconsistency(file, document) +
                "; node len=" + myTreeElementBeingReparsedSoItWontBeCollected.getTextLength() +
                "; doc.getText() == file.getText(): " + Comparing.equal(fileText, documentText),
                new Attachment("file psi text", fileText),
                new Attachment("old text", documentText));

      file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, Boolean.TRUE);
      try {
        BlockSupport blockSupport = BlockSupport.getInstance(file.getProject());
        final DiffLog diffLog = blockSupport.reparseRange(file, new TextRange(0, documentText.length()), documentText, createProgressIndicator(),
                                                          myTreeElementBeingReparsedSoItWontBeCollected.getText());
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
