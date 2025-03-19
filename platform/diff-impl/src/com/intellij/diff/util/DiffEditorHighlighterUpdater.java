// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileEditor.impl.text.EditorHighlighterUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

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

    restartHighlighterFor(project, editor);
  }

  /**
   * Force {@link DaemonCodeAnalyzer} to re-highlight the given Diff editor inside a not focused showed window.
   *
   * @see com.intellij.codeInsight.daemon.impl.EditorTrackerImpl#activeWindow
   * @see com.intellij.openapi.fileEditor.FileEditorWithTextEditors
   */
  private static void restartHighlighterFor(@Nullable Project project, @NotNull Editor editor) {
    if (project == null) return;

    // Rely on com.intellij.codeInsight.daemon.impl.EditorTracker and EditorTrackerListener.TOPIC in a focused window
    Window window = UIUtil.getWindow(editor.getComponent());
    if (window == null || !window.isShowing() || UIUtil.isFocusAncestor(window)) return;

    VirtualFile file = editor.getVirtualFile();
    if (file == null) return;

    ReadAction.nonBlocking(() -> {
        PsiManagerEx psiManager = PsiManagerEx.getInstanceEx(project);
        return psiManager.getFileManager().getCachedPsiFile(file);
      })
      .coalesceBy(DiffEditorHighlighterUpdater.class, editor)
      .expireWith(project)
      .submit(NonUrgentExecutor.getInstance())
      .onSuccess(psiFile -> {
        if (psiFile != null) {
          DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
        }
      });
  }
}
