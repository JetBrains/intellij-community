// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

public abstract class ProgressableTextEditorHighlightingPass extends TextEditorHighlightingPass {
  private volatile boolean myFinished;
  private volatile long myProgressLimit;
  private final AtomicLong myProgressCount = new AtomicLong();
  private final AtomicLong myNextChunkThreshold = new AtomicLong(); // the value myProgressCount should exceed to generate next fireProgressAdvanced event
  private final @NotNull @Nls String myPresentableName;
  protected final PsiFile myFile;
  private final @Nullable Editor myEditor;
  final @NotNull TextRange myRestrictRange;
  private final HighlightingSession myHighlightingSession;

  protected ProgressableTextEditorHighlightingPass(@NotNull Project project,
                                                   @NotNull Document document,
                                                   @NotNull @Nls String presentableName,
                                                   @Nullable PsiFile psiFile,
                                                   @Nullable Editor editor,
                                                   @NotNull TextRange restrictRange,
                                                   boolean runIntentionPassAfter,
                                                   @Deprecated
                                                   @Nullable("do not use") HighlightInfoProcessor highlightInfoProcessor) {
    super(project, document, runIntentionPassAfter);
    myPresentableName = presentableName;
    myFile = psiFile;
    myEditor = editor;
    myRestrictRange = restrictRange;
    if (psiFile != null) {
      if (psiFile.getProject() != project) {
        throw new IllegalArgumentException("File '" + psiFile +"' ("+psiFile.getClass()+") is from an alien project (" + psiFile.getProject()+") but expected: "+project);
      }
      if (InjectedLanguageManager.getInstance(project).isInjectedFragment(psiFile)) {
        throw new IllegalArgumentException("File '" + psiFile +"' ("+psiFile.getClass()+") is an injected fragment but expected top-level");
      }
      myHighlightingSession = HighlightingSessionImpl.getFromCurrentIndicator(psiFile);
    }
    else {
      myHighlightingSession = null;
    }
    if (document instanceof DocumentWindow) {
      throw new IllegalArgumentException("Document '" + document +" is an injected fragment but expected top-level");
    }
    if (editor != null) {
      PsiUtilBase.assertEditorAndProjectConsistent(project, editor);
      if (editor.getDocument() != document) {
        throw new IllegalArgumentException("Editor '" + editor + "' (" + editor.getClass() + ") has document " +editor.getDocument()+" but expected: "+document);
      }
    }
  }

  @Override
  protected boolean isValid() {
    return super.isValid() && (myFile == null || myFile.isValid());
  }

  private void sessionFinished() {
    advanceProgress(Math.max(1, myProgressLimit - myProgressCount.get()));
  }

  @Override
  public final void doCollectInformation(@NotNull ProgressIndicator progress) {
    GlobalInspectionContextBase.assertUnderDaemonProgress();
    ProgressManager.checkCanceled();
    myFinished = false;
    try {
      collectInformationWithProgress(progress);
    }
    finally {
      if (myFile != null) {
        sessionFinished();
      }
    }
  }

  protected abstract void collectInformationWithProgress(@NotNull ProgressIndicator progress);

  @Override
  public final void doApplyInformationToEditor() {
    myFinished = true;
    applyInformationWithProgress();
  }

  protected abstract void applyInformationWithProgress();

  /**
   * @return number in the [0..1] range;
   * <0 means progress is not available
   */
  public double getProgress() {
    long progressLimit = getProgressLimit();
    if (progressLimit == 0) return -1;
    long progressCount = getProgressCount();
    return progressCount > progressLimit ? 1 : (double)progressCount / progressLimit;
  }

  private long getProgressLimit() {
    return myProgressLimit;
  }

  private long getProgressCount() {
    return myProgressCount.get();
  }

  public boolean isFinished() {
    return myFinished;
  }

  protected @Nullable("null means do not show progress") @Nls String getPresentableName() {
    return myPresentableName;
  }

  protected Editor getEditor() {
    return myEditor;
  }

  public void setProgressLimit(long limit) {
    myProgressLimit = limit;
    myNextChunkThreshold.set(Math.max(1, limit / 100)); // 1% precision
  }

  public void advanceProgress(long delta) {
    // session can be null in e.g., inspection batch mode
    if (myHighlightingSession != null) {
      long current = myProgressCount.addAndGet(delta);
      if (current >= myNextChunkThreshold.get() &&
          current >= myNextChunkThreshold.updateAndGet(old -> current >= old ? old+Math.max(1, myProgressLimit / 100) : old)) {
        DaemonCodeAnalyzerEx.getInstanceEx(myProject).progressIsAdvanced(myHighlightingSession, getEditor(), getProgress());
      }
    }
  }

  static class EmptyPass extends TextEditorHighlightingPass {
    EmptyPass(@NotNull Project project, @NotNull Document document) {
      super(project, document, false);
    }

    @Override
    public void doCollectInformation(@NotNull ProgressIndicator progress) {
    }

    @Override
    public void doApplyInformationToEditor() {
    }
  }

  protected @NotNull HighlightingSession getHighlightingSession() {
    return myHighlightingSession;
  }
}
