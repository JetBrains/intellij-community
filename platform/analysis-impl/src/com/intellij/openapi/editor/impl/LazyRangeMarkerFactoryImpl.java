// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LazyRangeMarkerFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class LazyRangeMarkerFactoryImpl extends LazyRangeMarkerFactory {
  private final Project myProject;

  public LazyRangeMarkerFactoryImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(@NotNull final VirtualFile file, final int offset) {
    return ReadAction.compute(() -> DocumentImpl.createRangeMarkerForVirtualFile(file, offset, offset, -1, -1, -1, -1, false));
  }

  @Override
  @NotNull
  public RangeMarker createRangeMarker(@NotNull final VirtualFile file, final int line, final int column, final boolean persistent) {
    return ReadAction.compute(() -> {
      final Document document = file.getFileType().isBinary() ? null : FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        int myTabSize = CodeStyle.getFacade(myProject, document, file.getFileType()).getTabSize();
        final int offset = calculateOffset(document, line, column, myTabSize);
        return DocumentImpl.createRangeMarkerForVirtualFile(file, offset, offset, line, column, line, column, persistent);
      }

      return DocumentImpl.createRangeMarkerForVirtualFile(file, 0, 0, line, column, line, column, persistent);
    });
  }


  private static int calculateOffset(@NotNull Document document,
                                     final int line,
                                     final int column,
                                     int tabSize) {
    int offset;
    if (0 <= line && line < document.getLineCount()) {
      final int lineStart = document.getLineStartOffset(line);
      final int lineEnd = document.getLineEndOffset(line);
      final CharSequence docText = document.getCharsSequence();

      offset = lineStart;
      int col = 0;
      while (offset < lineEnd && col < column) {
        col += docText.charAt(offset) == '\t' ? tabSize : 1;
        offset++;
      }
    }
    else {
      offset = document.getTextLength();
    }
    return offset;
  }
}
