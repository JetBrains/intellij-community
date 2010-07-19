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
package com.intellij.codeHighlighting;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TextEditorHighlightingPass implements HighlightingPass {
  public static final TextEditorHighlightingPass[] EMPTY_ARRAY = new TextEditorHighlightingPass[0];
  protected final Document myDocument;
  protected final Project myProject;
  private final boolean myRunIntentionPassAfter;
  private final long myInitialStamp;
  private int[] myCompletionPredecessorIds = ArrayUtil.EMPTY_INT_ARRAY;
  private int[] myStartingPredecessorIds = ArrayUtil.EMPTY_INT_ARRAY;
  private int myId;
  private boolean myDumb;

  protected TextEditorHighlightingPass(@NotNull final Project project, @Nullable final Document document, boolean runIntentionPassAfter) {
    myDocument = document;
    myProject = project;
    myRunIntentionPassAfter = runIntentionPassAfter;
    myInitialStamp = document == null ? 0 : document.getModificationStamp();
  }
  protected TextEditorHighlightingPass(@NotNull final Project project, @Nullable final Document document) {
    this(project, document, true);
  }

  public final void collectInformation(ProgressIndicator progress) {
    if (!isValid()) return; //Document has changed.
    myDumb = DumbService.getInstance(myProject).isDumb();
    doCollectInformation(progress);
  }

  protected boolean isDumbMode() {
    return myDumb;
  }

  private boolean isValid() {
    if (isDumbMode() && !DumbService.isDumbAware(this)) {
      return false;
    }

    if (myDocument != null && myDocument.getModificationStamp() != myInitialStamp) return false;
    if (myProject != null && myDocument != null) {
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
      if (file == null || !file.isValid()) return false;
    }

    return true;
  }

  public final void applyInformationToEditor() {
    if (!isValid()) return; // Document has changed.
    doApplyInformationToEditor();
  }

  public abstract void doCollectInformation(ProgressIndicator progress);
  public abstract void doApplyInformationToEditor();

  public final int getId() {
    return myId;
  }

  public final void setId(final int id) {
    myId = id;
  }

  @NotNull
  public final int[] getCompletionPredecessorIds() {
    return myCompletionPredecessorIds;
  }

  public final void setCompletionPredecessorIds(@NotNull int[] completionPredecessorIds) {
    myCompletionPredecessorIds = completionPredecessorIds;
  }

  public Document getDocument() {
    return myDocument;
  }

  @NotNull public final int[] getStartingPredecessorIds() {
    return myStartingPredecessorIds;
  }

  public final void setStartingPredecessorIds(@NotNull final int[] startingPredecessorIds) {
    myStartingPredecessorIds = startingPredecessorIds;
  }

  @NonNls
  public String toString() {
    return getClass() + "; id=" + getId();
  }

  public boolean isRunIntentionPassAfter() {
    return myRunIntentionPassAfter;
  }
}