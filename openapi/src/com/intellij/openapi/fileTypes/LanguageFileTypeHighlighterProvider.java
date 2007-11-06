package com.intellij.openapi.fileTypes;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class LanguageFileTypeHighlighterProvider implements SyntaxHighlighterProvider {
  @Nullable
  public SyntaxHighlighter create(final FileType fileType, @Nullable final Project project, @Nullable final VirtualFile file) {
    if (fileType instanceof LanguageFileType) {
      return ((LanguageFileType) fileType).getLanguage().getSyntaxHighlighter(project, file);
    }
    return null;
  }
}