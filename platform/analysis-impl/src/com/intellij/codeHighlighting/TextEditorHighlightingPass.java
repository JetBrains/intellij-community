// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeHighlighting;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContexts;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * The highlighting pass which is associated with {@link Document} and its markup model.
 * The instantiation of this class must happen in the background thread, under {@link com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator}
 * which has corresponding {@link com.intellij.codeInsight.daemon.impl.HighlightingSession}.
 * It's discouraged to do all that manually, please register your {@link TextEditorHighlightingPassFactory} in plugin.xml instead, e.g. like this:
 * <pre>
 *   {@code <highlightingPassFactory implementation="com.a.b.MyPassFactory"/>}
 * </pre>
 */
public abstract class TextEditorHighlightingPass implements HighlightingPass {
  public static final TextEditorHighlightingPass[] EMPTY_ARRAY = new TextEditorHighlightingPass[0];
  protected final @NotNull Document myDocument;
  protected final @NotNull Project myProject;
  private final boolean myRunIntentionPassAfter;
  private final long myInitialDocStamp;
  private final long myInitialPsiStamp;
  private volatile int[] myCompletionPredecessorIds = ArrayUtilRt.EMPTY_INT_ARRAY;
  private volatile int[] myStartingPredecessorIds = ArrayUtilRt.EMPTY_INT_ARRAY;
  private volatile int myId;
  private volatile boolean myDumb;
  private EditorColorsScheme myColorsScheme;
  private volatile CodeInsightContext myContext;

  protected TextEditorHighlightingPass(@NotNull Project project, @NotNull Document document, boolean runIntentionPassAfter) {
    myDocument = document;
    myProject = project;
    myRunIntentionPassAfter = runIntentionPassAfter;
    myInitialDocStamp = document.getModificationStamp();
    myInitialPsiStamp = PsiModificationTracker.getInstance(project).getModificationCount();
    ThreadingAssertions.assertBackgroundThread();
  }
  protected TextEditorHighlightingPass(@NotNull Project project, @NotNull Document document) {
    this(project, document, true);
  }

  @Override
  public final void collectInformation(@NotNull ProgressIndicator progress) {
    if (!isValid()) return; //the document has changed.
    GlobalInspectionContextBase.assertUnderDaemonProgress();
    myDumb = DumbService.getInstance(myProject).isDumb();
    doCollectInformation(progress);
  }

  public @Nullable EditorColorsScheme getColorsScheme() {
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

  /**
   * @return true if the file being highlighted hasn't changed since the pass instantiation and the highlighting results can be applied safely.
   */
  protected boolean isValid() {
    if (myProject.isDisposed()) {
      return false;
    }
    if (!DumbService.getInstance(myProject).isUsableInCurrentContext(this)) {
      return false;
    }

    if (PsiModificationTracker.getInstance(myProject).getModificationCount() != myInitialPsiStamp) {
      return false;
    }

    if (myDocument.getModificationStamp() != myInitialDocStamp) return false;
    CodeInsightContext codeInsightContext = getContext();
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument, codeInsightContext);
    PsiElement context;
    return file != null
           && file.isValid()
           && ((context = file.getContext()) == null || context == file || context.isValid());
  }

  @Override
  public final void applyInformationToEditor() {
    if (!isValid()) {
      return; // the document has changed.
    }
    if (DumbService.getInstance(myProject).isUsableInCurrentContext(this)) {
      doApplyInformationToEditor();
    }
  }

  @ApiStatus.Internal
  public void markUpToDateIfStillValid(@NotNull DaemonProgressIndicator updateProgress) {
    ThreadingAssertions.assertEventDispatchThread();
    if (isValid()) {
      DaemonCodeAnalyzerEx.getInstanceEx(myProject).getFileStatusMap().markFileUpToDate(getDocument(), getContext(), getId(), updateProgress);
    }
  }

  public abstract void doCollectInformation(@NotNull ProgressIndicator progress);
  public abstract void doApplyInformationToEditor();

  public final int getId() {
    return myId;
  }

  public final void setId(int id) {
    myId = id;
  }

  public @NotNull List<HighlightInfo> getInfos() {
    return Collections.emptyList();
  }

  public final int @NotNull [] getCompletionPredecessorIds() {
    return myCompletionPredecessorIds;
  }

  public final void setCompletionPredecessorIds(int @NotNull [] completionPredecessorIds) {
    myCompletionPredecessorIds = completionPredecessorIds;
  }

  public @NotNull Document getDocument() {
    return myDocument;
  }

  @ApiStatus.Internal
  public void setContext(@NotNull CodeInsightContext context) {
    assert myContext == null : "context is already assigned";
    myContext = context;
  }

  @ApiStatus.Experimental
  protected @NotNull CodeInsightContext getContext() {
    if (myContext == null) {
      // todo IJPL-339 report an error here once all the highlighting passes are ready
      //      LOG.error("context was not set");
      return CodeInsightContexts.anyContext();
    }
    return myContext;
  }

  public final int @NotNull [] getStartingPredecessorIds() {
    return myStartingPredecessorIds;
  }

  public final void setStartingPredecessorIds(int @NotNull [] startingPredecessorIds) {
    myStartingPredecessorIds = startingPredecessorIds;
  }

  @Override
  public @NonNls String toString() {
    return (getClass().isAnonymousClass() ? getClass().getSuperclass() : getClass()).getSimpleName() + "; id=" + getId();
  }

  public boolean isRunIntentionPassAfter() {
    return myRunIntentionPassAfter;
  }
}
