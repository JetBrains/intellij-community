package com.intellij.ide.highlighter.custom.impl;

import com.intellij.openapi.fileTypes.SyntaxHighlighterProvider;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ide.highlighter.custom.CustomFileHighlighter;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class CustomFileTypeHighlighterProvider implements SyntaxHighlighterProvider {
  @Nullable
  public SyntaxHighlighter create(final FileType fileType, @Nullable final Project project, @Nullable final VirtualFile file) {
    if (fileType instanceof CustomFileType) {
      return new CustomFileHighlighter(((CustomFileType) fileType).getSyntaxTable());
    }
    return null;
  }
}