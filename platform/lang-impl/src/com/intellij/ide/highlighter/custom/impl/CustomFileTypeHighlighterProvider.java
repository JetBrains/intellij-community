// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.highlighter.custom.impl;

import com.intellij.ide.highlighter.custom.CustomFileHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterProvider;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class CustomFileTypeHighlighterProvider implements SyntaxHighlighterProvider {
  @Override
  @Nullable
  public SyntaxHighlighter create(@NotNull final FileType fileType, @Nullable final Project project, @Nullable final VirtualFile file) {
    if (fileType instanceof CustomSyntaxTableFileType) {
      return new CustomFileHighlighter(((CustomSyntaxTableFileType) fileType).getSyntaxTable());
    }
    return null;
  }
}