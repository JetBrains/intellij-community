// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class LanguageFileTypeHighlighterProvider implements SyntaxHighlighterProvider {
  @Override
  public @Nullable SyntaxHighlighter create(final @NotNull FileType fileType, final @Nullable Project project, final @Nullable VirtualFile file) {
    if (fileType instanceof LanguageFileType) {
      return SyntaxHighlighterFactory.getSyntaxHighlighter(((LanguageFileType)fileType).getLanguage(), project, file);
    }
    return null;
  }
}