// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;


import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorFacade;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class EditorFacadeImpl extends EditorFacade {


  @Override
  public void doWrapLongLinesIfNecessary(@NotNull Editor editor,
                                         @NotNull Project project,
                                         @NotNull Document document,
                                         int startOffset,
                                         int endOffset,
                                         List<? extends TextRange> enabledRanges,
                                         int rightMargin) {
    LineWrappingUtil.doWrapLongLinesIfNecessary(editor, project, document, startOffset, endOffset, enabledRanges, rightMargin);
  }
}
