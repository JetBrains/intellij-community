// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;


public class DiffEditorHighlighterUpdater extends EditorHighlighterUpdater {
  @NotNull private final DocumentContent myContent;

  public DiffEditorHighlighterUpdater(@NotNull Project project,
                                      @NotNull Disposable parentDisposable,
                                      @NotNull EditorEx editor,
                                      @NotNull DocumentContent content) {
    super(project, parentDisposable, editor, content.getHighlightFile());
    myContent = content;
  }

  @NotNull
  @Override
  protected EditorHighlighter createHighlighter(boolean forceEmpty) {
    if (!forceEmpty) {
      CharSequence text = myEditor.getDocument().getImmutableCharSequence();
      EditorHighlighter highlighter = DiffUtil.initEditorHighlighter(myProject, myContent, text);
      if (highlighter != null) {
        return highlighter;
      }
    }
    return DiffUtil.createEmptyEditorHighlighter();
  }

  @Override
  protected void setupHighlighter(@NotNull EditorHighlighter highlighter) {
    super.setupHighlighter(highlighter);

    restartHighlighterFor(myProject, myEditor);
  }

  /**
   * Force {@link DaemonCodeAnalyzer} to re-highlight the given Diff editor inside a not focused showed window.
   *
   * @see com.intellij.codeInsight.daemon.impl.EditorTrackerImpl#activeWindow
   * @see com.intellij.openapi.fileEditor.FileEditorWithTextEditors
   */
  private static void restartHighlighterFor(@Nullable Project project, @NotNull Editor editor) {
    if (project == null) return;

    // rely on com.intellij.codeInsight.daemon.impl.EditorTracker if in focused window
    Window window = UIUtil.getWindow(editor.getComponent());
    if (window == null || !window.isShowing() || UIUtil.isFocusAncestor(window)) return;

    PsiManagerEx psiManager = PsiManagerEx.getInstanceEx(project);
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);

    VirtualFile file = editor.getVirtualFile();
    if (file == null) return;

    PsiFile psiFile = ReadAction.compute(() -> psiManager.getFileManager().getCachedPsiFile(file));
    if (psiFile != null) {
      codeAnalyzer.restart(psiFile);
    }
  }
}
