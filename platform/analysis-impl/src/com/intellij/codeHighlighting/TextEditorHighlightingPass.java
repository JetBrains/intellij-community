// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeHighlighting;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class TextEditorHighlightingPass implements HighlightingPass {
  public static final TextEditorHighlightingPass[] EMPTY_ARRAY = new TextEditorHighlightingPass[0];
  @NotNull
  protected final Document myDocument;
  @NotNull
  protected final Project myProject;
  private final boolean myRunIntentionPassAfter;
  private final long myInitialDocStamp;
  private final long myInitialPsiStamp;
  private volatile int[] myCompletionPredecessorIds = ArrayUtilRt.EMPTY_INT_ARRAY;
  private volatile int[] myStartingPredecessorIds = ArrayUtilRt.EMPTY_INT_ARRAY;
  private volatile int myId;
  private volatile boolean myDumb;
  private EditorColorsScheme myColorsScheme;

  protected TextEditorHighlightingPass(@NotNull Project project, @NotNull Document document, boolean runIntentionPassAfter) {
    myDocument = document;
    myProject = project;
    myRunIntentionPassAfter = runIntentionPassAfter;
    myInitialDocStamp = document.getModificationStamp();
    myInitialPsiStamp = PsiModificationTracker.SERVICE.getInstance(myProject).getModificationCount();
  }
  protected TextEditorHighlightingPass(@NotNull Project project, @NotNull Document document) {
    this(project, document, true);
  }

  @Override
  public final void collectInformation(@NotNull ProgressIndicator progress) {
    if (!isValid()) return; //Document has changed.
    GlobalInspectionContextBase.assertUnderDaemonProgress();
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

  @Override
  public @NotNull Condition<?> getExpiredCondition() {
    return (Condition<Object>)o -> ReadAction.compute(() -> !isValid());
  }

  protected boolean isValid() {
    if (myProject.isDisposed()) {
      return false;
    }
    if (isDumbMode() && !DumbService.isDumbAware(this)) {
      return false;
    }

    if (PsiModificationTracker.SERVICE.getInstance(myProject).getModificationCount() != myInitialPsiStamp) {
      return false;
    }

    if (myDocument.getModificationStamp() != myInitialDocStamp) return false;
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    return file != null && file.isValid();
  }

  @Override
  public final void applyInformationToEditor() {
    if (!isValid()) return; // Document has changed.
    if (DumbService.getInstance(myProject).isDumb() && !DumbService.isDumbAware(this)) {
      Document document = getDocument();
      PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
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

  public final void setId(int id) {
    myId = id;
  }

  @NotNull
  public List<HighlightInfo> getInfos() {
    return Collections.emptyList();
  }

  public final int @NotNull [] getCompletionPredecessorIds() {
    return myCompletionPredecessorIds;
  }

  public final void setCompletionPredecessorIds(int @NotNull [] completionPredecessorIds) {
    myCompletionPredecessorIds = completionPredecessorIds;
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  public final int @NotNull [] getStartingPredecessorIds() {
    return myStartingPredecessorIds;
  }

  public final void setStartingPredecessorIds(int @NotNull [] startingPredecessorIds) {
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
