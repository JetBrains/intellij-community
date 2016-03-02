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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author cdr
 */
public abstract class ProgressableTextEditorHighlightingPass extends TextEditorHighlightingPass {
  private volatile boolean myFinished;
  private volatile long myProgressLimit;
  private final AtomicLong myProgressCount = new AtomicLong();
  private volatile long myNextChunkThreshold; // the value myProgressCount should exceed to generate next fireProgressAdvanced event
  private final String myPresentableName;
  protected final PsiFile myFile;
  @Nullable private final Editor myEditor;
  @NotNull protected final TextRange myRestrictRange;
  @NotNull protected final HighlightInfoProcessor myHighlightInfoProcessor;
  protected HighlightingSession myHighlightingSession;

  protected ProgressableTextEditorHighlightingPass(@NotNull Project project,
                                                   @Nullable final Document document,
                                                   @NotNull String presentableName,
                                                   @Nullable PsiFile file,
                                                   @Nullable Editor editor,
                                                   @NotNull TextRange restrictRange,
                                                   boolean runIntentionPassAfter,
                                                   @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    super(project, document, runIntentionPassAfter);
    myPresentableName = presentableName;
    myFile = file;
    myEditor = editor;
    myRestrictRange = restrictRange;
    myHighlightInfoProcessor = highlightInfoProcessor;
  }

  @Override
  protected boolean isValid() {
    return super.isValid() && (myFile == null || myFile.isValid());
  }

  private void sessionFinished() {
    advanceProgress(Math.max(1, myProgressLimit - myProgressCount.get()));
  }

  @Override
  public final void doCollectInformation(@NotNull final ProgressIndicator progress) {
    if (!(progress instanceof DaemonProgressIndicator)) {
      throw new IncorrectOperationException("Highlighting must be run under DaemonProgressIndicator, but got: "+progress);
    }
    myFinished = false;
    if (myFile != null) {
      myHighlightingSession = HighlightingSessionImpl.getOrCreateHighlightingSession(myFile, myEditor, (DaemonProgressIndicator)progress, getColorsScheme());
    }
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
    DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    daemonCodeAnalyzer.getFileStatusMap().markFileUpToDate(myDocument, getId());
    if (myHighlightingSession != null) {
      myHighlightInfoProcessor.progressIsAdvanced(myHighlightingSession, 1);  //causes traffic light repaint
    }
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

  protected String getPresentableName() {
    return myPresentableName;
  }

  protected Editor getEditor() {
    return myEditor;
  }

  public void setProgressLimit(long limit) {
    myProgressLimit = limit;
    myNextChunkThreshold = Math.max(1, limit / 100); // 1% precision
  }

  public void advanceProgress(long delta) {
    if (myHighlightingSession != null) {
      // session can be null in e.g. inspection batch mode
      long current = myProgressCount.addAndGet(delta);
      if (current >= myNextChunkThreshold) {
        double progress = getProgress();
        myNextChunkThreshold += Math.max(1, myProgressLimit / 100);
        myHighlightInfoProcessor.progressIsAdvanced(myHighlightingSession, progress);
      }
    }
  }

  static class EmptyPass extends TextEditorHighlightingPass {
    public EmptyPass(final Project project, @Nullable final Document document) {
      super(project, document, false);
    }

    @Override
    public void doCollectInformation(@NotNull final ProgressIndicator progress) {
    }

    @Override
    public void doApplyInformationToEditor() {
      FileStatusMap statusMap = DaemonCodeAnalyzerEx.getInstanceEx(myProject).getFileStatusMap();
      statusMap.markFileUpToDate(getDocument(), getId());
    }
  }
}
