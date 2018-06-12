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
package com.intellij.codeHighlighting;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class TextEditorHighlightingPass implements HighlightingPass {
  public static final TextEditorHighlightingPass[] EMPTY_ARRAY = new TextEditorHighlightingPass[0];
  @Nullable protected final Document myDocument;
  @NotNull protected final Project myProject;
  private final boolean myRunIntentionPassAfter;
  private final long myInitialDocStamp;
  private final long myInitialPsiStamp;
  private volatile int[] myCompletionPredecessorIds = ArrayUtil.EMPTY_INT_ARRAY;
  private volatile int[] myStartingPredecessorIds = ArrayUtil.EMPTY_INT_ARRAY;
  private volatile int myId;
  private volatile boolean myDumb;
  private EditorColorsScheme myColorsScheme;

  protected TextEditorHighlightingPass(@NotNull final Project project, @Nullable final Document document, boolean runIntentionPassAfter) {
    myDocument = document;
    myProject = project;
    myRunIntentionPassAfter = runIntentionPassAfter;
    myInitialDocStamp = document == null ? 0 : document.getModificationStamp();
    myInitialPsiStamp = PsiModificationTracker.SERVICE.getInstance(myProject).getModificationCount();
  }
  protected TextEditorHighlightingPass(@NotNull final Project project, @Nullable final Document document) {
    this(project, document, true);
  }

  @Override
  public final void collectInformation(@NotNull ProgressIndicator progress) {
    if (!isValid()) return; //Document has changed.
    if (!(progress instanceof DaemonProgressIndicator)) {
      throw new IncorrectOperationException("Highlighting must be run under DaemonProgressIndicator, but got: "+progress);
    }
    myDumb = DumbService.getInstance(myProject).isDumb();
    doCollectInformation(progress);
  }

  @Nullable
  public EditorColorsScheme getColorsScheme() {
    return myColorsScheme;
  }

  public void setColorsScheme(@Nullable EditorColorsScheme colorsScheme) {
    myColorsScheme = colorsScheme;
  }

  protected boolean isDumbMode() {
    return myDumb;
  }

  protected boolean isValid() {
    if (isDumbMode() && !DumbService.isDumbAware(this)) {
      return false;
    }

    if (PsiModificationTracker.SERVICE.getInstance(myProject).getModificationCount() != myInitialPsiStamp) {
      return false;
    }

    if (myDocument != null) {
      if (myDocument.getModificationStamp() != myInitialDocStamp) return false;
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
      return file != null && file.isValid();
    }

    return true;
  }

  @Override
  public final void applyInformationToEditor() {
    if (!isValid()) return; // Document has changed.
    if (DumbService.getInstance(myProject).isDumb() && !DumbService.isDumbAware(this)) {
      Document document = getDocument();
      PsiFile file = document == null ? null : PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      if (file != null) {
        DaemonCodeAnalyzerEx.getInstanceEx(myProject).getFileStatusMap().markFileUpToDate(getDocument(), getId());
      }
      return;
    }
    doApplyInformationToEditor();
  }

  public abstract void doCollectInformation(@NotNull ProgressIndicator progress);
  public abstract void doApplyInformationToEditor();

  public final int getId() {
    return myId;
  }

  public final void setId(final int id) {
    myId = id;
  }

  @NotNull
  public List<HighlightInfo> getInfos() {
    return Collections.emptyList();
  }

  @NotNull
  public final int[] getCompletionPredecessorIds() {
    return myCompletionPredecessorIds;
  }

  public final void setCompletionPredecessorIds(@NotNull int[] completionPredecessorIds) {
    myCompletionPredecessorIds = completionPredecessorIds;
  }

  @Nullable
  public Document getDocument() {
    return myDocument;
  }

  @NotNull public final int[] getStartingPredecessorIds() {
    return myStartingPredecessorIds;
  }

  public final void setStartingPredecessorIds(@NotNull final int[] startingPredecessorIds) {
    myStartingPredecessorIds = startingPredecessorIds;
  }

  @Override
  @NonNls
  public String toString() {
    return (getClass().isAnonymousClass() ? getClass().getSuperclass() : getClass()).getSimpleName() + "; id=" + getId();
  }

  public boolean isRunIntentionPassAfter() {
    return myRunIntentionPassAfter;
  }
}
