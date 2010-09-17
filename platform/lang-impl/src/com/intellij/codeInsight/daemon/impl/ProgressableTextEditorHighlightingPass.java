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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
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
                                                   @NotNull PsiFile file,
                                                   boolean runIntentionPassAfter) {
    super(project, document, runIntentionPassAfter);
    myPresentableName = presentableName;
    myFile = file;
  }

  public final void doCollectInformation(final ProgressIndicator progress) {
    myFinished = false;
    collectInformationWithProgress(progress);
    repaintTrafficIcon();
  }

  protected abstract void collectInformationWithProgress(final ProgressIndicator progress);

  public final void doApplyInformationToEditor() {
    myFinished = true;
    applyInformationWithProgress();
    DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).getFileStatusMap().markFileUpToDate(myDocument, myFile, getId());
    repaintTrafficIcon();
  }

  protected abstract void applyInformationWithProgress();

  /**
   * @return number in the [0..1] range;
   * <0 means progress is not available
   */
  public double getProgress() {
    if (myProgessLimit == 0) return -1;
    return ((double)myProgressCount.get()) / myProgessLimit;
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

  private final Alarm repaintIconAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  public void advanceProgress(long delta) {
    long l = myProgressCount.addAndGet(delta);
    repaintTrafficIcon();
  }

  private void repaintTrafficIcon() {
    if (ApplicationManager.getApplication().isCommandLine()) return;

    if (repaintIconAlarm.getActiveRequestCount() == 0 || getProgressCount() >= getProgressLimit()) {
      repaintIconAlarm.addRequest(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          Editor editor = getEditorForFile(myFile);
          if (editor == null || editor.isDisposed()) return;
          EditorMarkupModelImpl markup = (EditorMarkupModelImpl)editor.getMarkupModel();
          markup.repaintTrafficLightIcon();
        }
      }, 50, null);
    }
  }

  private static Editor getEditorForFile(@NotNull final PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      PsiFile originalFile = psiFile.getOriginalFile();
      virtualFile = originalFile.getVirtualFile();
      if (virtualFile == null) return null;
    }
    final FileEditor[] editors = FileEditorManager.getInstance(psiFile.getProject()).getEditors(virtualFile);
    for (FileEditor editor : editors) {
      if (editor instanceof TextEditor) {
        return ((TextEditor)editor).getEditor();
      }
    }
    return null;
  }
  
  public static class EmptyPass extends TextEditorHighlightingPass {
    public EmptyPass(final Project project, @Nullable final Document document) {
      super(project, document, false);
    }

    public void doCollectInformation(final ProgressIndicator progress) {

    }

    public void doApplyInformationToEditor() {

    }
  }
}
