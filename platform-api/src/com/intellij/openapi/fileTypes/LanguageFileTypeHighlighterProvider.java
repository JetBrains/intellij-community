package com.intellij.openapi.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class LanguageFileTypeHighlighterProvider implements SyntaxHighlighterProvider {
  @Nullable
  public SyntaxHighlighter create(final FileType fileType, @Nullable final Project project, @Nullable final VirtualFile file) {
    if (fileType instanceof LanguageFileType) {
      return SyntaxHighlighterFactory.getSyntaxHighlighter(((LanguageFileType)fileType).getLanguage(), project, file);
    }
    return null;
  }
}