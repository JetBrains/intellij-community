// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LazyRangeMarkerFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class LazyRangeMarkerFactoryImpl extends LazyRangeMarkerFactory {
  private final Project myProject;

  public LazyRangeMarkerFactoryImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull RangeMarker createRangeMarker(@NotNull VirtualFile file, int offset) {
    return ReadAction.compute(() -> DocumentImpl.createRangeMarkerForVirtualFile(file, offset, -1, -1, -1, -1, false));
  }

  @Override
  public @NotNull RangeMarker createRangeMarker(@NotNull VirtualFile file, int line, int column, boolean persistent) {
    return ReadAction.compute(() -> {
      Document document = file.getFileType().isBinary() ? null : FileDocumentManager.getInstance().getCachedDocument(file);
      if (document != null) {
        int offset = DocumentUtil.calculateOffset(document, line, column, CodeStyle.getFacade(myProject, document, file.getFileType()).getTabSize());
        return DocumentImpl.createRangeMarkerForVirtualFile(file, offset, line, column, line, column, persistent);
      }

      return DocumentImpl.createRangeMarkerForVirtualFile(file, 0, line, column, line, column, true); // must be persistent to be able to restore from line/col
    });
  }
}
