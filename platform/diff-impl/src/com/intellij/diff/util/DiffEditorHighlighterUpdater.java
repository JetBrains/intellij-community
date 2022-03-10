// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import com.intellij.diff.contents.DocumentContent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileEditor.impl.text.EditorHighlighterUpdater;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;


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
}
