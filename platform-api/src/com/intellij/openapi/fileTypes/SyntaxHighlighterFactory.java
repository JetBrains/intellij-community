/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class SyntaxHighlighterFactory {
  public static SyntaxHighlighterLanguageFactory LANGUAGE_FACTORY = new SyntaxHighlighterLanguageFactory();

  public static SyntaxHighlighter getSyntaxHighlighter(Language lang, Project project, VirtualFile virtualFile) {
    return LANGUAGE_FACTORY.forLanguage(lang).getSyntaxHighlighter(project, virtualFile);
  }


  /**
   * Override this method to provide syntax highlighting (coloring) capabilities for your language implementation.
   * By syntax highlighting we mean highlighting of keywords, comments, braces etc. where lexing the file content is enough
   * to identify proper highlighting attributes.
   * <p/>
   * Default implementation doesn't highlight anything.
   *
   * @param project might be necessary to gather various project settings from.
   * @param virtualFile might be necessary to collect file specific settings
   * @return <code>SyntaxHighlighter</code> interface implementation for this particular language.
   */
  @NotNull
  public abstract SyntaxHighlighter getSyntaxHighlighter(Project project, final VirtualFile virtualFile);
}