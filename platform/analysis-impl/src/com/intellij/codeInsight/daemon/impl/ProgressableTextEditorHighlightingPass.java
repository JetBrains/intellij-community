/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author cdr
 */
public abstract class ProgressableTextEditorHighlightingPass extends TextEditorHighlightingPass {
  private volatile boolean myFinished;
  private volatile long myProgessLimit = 0;
  private final AtomicLong myProgressCount = new AtomicLong();
  private final String myPresentableName;
  protected final PsiFile myFile;

  protected ProgressableTextEditorHighlightingPass(@NotNull Project project,
                                                   @Nullable final Document document,
                                                   @NotNull String presentableName,
                                                   @Nullable PsiFile file,
                                                   boolean runIntentionPassAfter) {
    super(project, document, runIntentionPassAfter);
    myPresentableName = presentableName;
    myFile = file;
  }

  @Override
  public final void doCollectInformation(@NotNull final ProgressIndicator progress) {
    myFinished = false;
    collectInformationWithProgress(progress);
    fireProgressHasAdvanced();
  }

  protected abstract void collectInformationWithProgress(@NotNull ProgressIndicator progress);

  @Override
  public final void doApplyInformationToEditor() {
    myFinished = true;
    applyInformationWithProgress();
    DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    daemonCodeAnalyzer.getFileStatusMap().markFileUpToDate(myDocument, getId());
    fireProgressHasAdvanced();
  }

  private void fireProgressHasAdvanced() {
    myProject.getMessageBus().syncPublisher(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC).passProgressHasAdvanced(myFile, getProgress());
  }

  protected abstract void applyInformationWithProgress();

  /**
   * @return number in the [0..1] range;
   * <0 means progress is not available
   */
  public double getProgress() {
    if (myProgessLimit == 0) return -1;
    return (double)myProgressCount.get() / myProgessLimit;
  }

  public long getProgressLimit() {
    return myProgessLimit;
  }

  public long getProgressCount() {
    return myProgressCount.get();
  }

  public boolean isFinished() {
    return myFinished;
  }

  protected String getPresentableName() {
    return myPresentableName;
  }

  public void setProgressLimit(long limit) {
    myProgessLimit = limit;
  }

  public void advanceProgress(long delta) {
    myProgressCount.addAndGet(delta);
    fireProgressHasAdvanced();
  }

  public static class EmptyPass extends TextEditorHighlightingPass {
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
