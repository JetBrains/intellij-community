// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileEditor.impl.text.EditorHighlighterUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Window;

@ApiStatus.Internal
public class DiffEditorHighlighterUpdater extends EditorHighlighterUpdater {
  private final @NotNull DocumentContent myContent;

  public DiffEditorHighlighterUpdater(@NotNull Project project,
                                      @NotNull Disposable parentDisposable,
                                      @NotNull EditorEx editor,
                                      @NotNull DocumentContent content) {
    super(project, parentDisposable, project.getMessageBus().connect(parentDisposable), editor, content.getHighlightFile(), null);
    myContent = content;
  }

  @Override
  protected @NotNull EditorHighlighter createHighlighter(boolean forceEmpty) {
    if (!forceEmpty) {
      CharSequence text = editor.getDocument().getImmutableCharSequence();
      EditorHighlighter highlighter = DiffUtil.initEditorHighlighter(project, myContent, text);
      if (highlighter != null) {
        return highlighter;
      }
    }
    return DiffUtil.createEmptyEditorHighlighter();
  }

  @Override
  protected void setupHighlighter(@NotNull EditorHighlighter highlighter) {
    super.setupHighlighter(highlighter);

    restartHighlighterInWindow(project, editor, myContent.getDocument(), this);
  }

  /**
   * Force {@link DaemonCodeAnalyzer} to re-highlight the given Diff editor inside a not focused showed window.
   *
   * @see com.intellij.codeInsight.daemon.impl.EditorTrackerImpl#activeWindow
   * @see com.intellij.openapi.fileEditor.FileEditorWithTextEditors
   */
  private static void restartHighlighterInWindow(@Nullable Project project,
                                                 @NotNull Editor editor,
                                                 @NotNull Document document,
                                                 @NotNull @NonNls Object restartReason) {
    if (project == null) return;

    // Rely on com.intellij.codeInsight.daemon.impl.EditorTracker and EditorTrackerListener.TOPIC in a focused window
    Window window = UIUtil.getWindow(editor.getComponent());
    if (window == null || !window.isShowing() || UIUtil.isFocusAncestor(window)) return;

    restartHighlighterFor(project, editor, document, restartReason);
  }

  /**
   * Force {@link DaemonCodeAnalyzer} to re-highlight the document.
   *
   * @see DaemonCodeAnalyzer#restart(com.intellij.psi.PsiFile, Object)
   */
  public static void restartHighlighterFor(@NotNull Project project,
                                           @NotNull Editor editor,
                                           @NotNull Document document,
                                           @NotNull @NonNls Object restartReason) {
    ReadAction.nonBlocking(() -> {
        return PsiDocumentManager.getInstance(project).getCachedPsiFile(document);
      })
      .coalesceBy(DiffEditorHighlighterUpdater.class, editor)
      .expireWith(project)
      .submit(NonUrgentExecutor.getInstance())
      .onSuccess(psiFile -> {
        if (psiFile != null) {
          if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            DaemonCodeAnalyzer.getInstance(project).restart(psiFile, restartReason);
          } else {
            ApplicationManager.getApplication().invokeLater(() -> DaemonCodeAnalyzer.getInstance(project).restart(psiFile, restartReason));
          }
        }
      });
  }
}
