// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This extension point <pre>{@code <lang.syntaxHighlighterFactory>}</pre> allows highlighting subsystem to provide syntax highlighting for the particular file.
 * By "syntax highlighting" we mean highlighting of keywords, comments, braces etc. where lexing the file content is enough.
 *
 * To provide rich highlighting based on PSI see {@link com.intellij.codeInspection.LocalInspectionTool} or {@link com.intellij.lang.annotation.Annotator}
 * @see SingleLazyInstanceSyntaxHighlighterFactory
 */
public abstract class SyntaxHighlighterFactory {

  /**
   * @deprecated use {@link #getLanguageFactory()} instead
   */
  @Deprecated
  public static final SyntaxHighlighterLanguageFactory LANGUAGE_FACTORY = new SyntaxHighlighterLanguageFactory();

  public static SyntaxHighlighterLanguageFactory getLanguageFactory() {
    return LANGUAGE_FACTORY;
  }

  /**
   * Returns syntax highlighter for the given language.
   * Requires read lock because some implementations of {@link #getSyntaxHighlighter(Project, VirtualFile)}
   * may invoke operations that need it.
   * @param language a {@code Language} to get highlighter for
   * @param project  might be necessary to gather various project settings from
   * @param file     might be necessary to collect file specific settings
   * @return {@code SyntaxHighlighter} interface implementation for the given file type
   */
  @RequiresReadLock(generateAssertion = false)
  public static SyntaxHighlighter getSyntaxHighlighter(@NotNull Language language, @Nullable Project project, @Nullable VirtualFile file) {
    return getLanguageFactory().forLanguage(language).getSyntaxHighlighter(project, file);
  }

  /**
   * Returns syntax highlighter for the given file type.
   * Note: it is recommended to use {@link #getSyntaxHighlighter(Language, Project, VirtualFile)} in most cases,
   * and use this method only when do not know the language you use.
   * </p>
   * Requires read lock because some implementations of {@link SyntaxHighlighterProvider#create(FileType, Project, VirtualFile)}
   * may invoke operations that need it.
   * @param fileType a file type to use to select appropriate highlighter
   * @param project  might be necessary to gather various project settings from
   * @param file     might be necessary to collect file specific settings
   * @return {@code SyntaxHighlighter} interface implementation for the given file type
   */
  @RequiresReadLock(generateAssertion = false)
  public static @Nullable SyntaxHighlighter getSyntaxHighlighter(@NotNull FileType fileType, @Nullable Project project, @Nullable VirtualFile file) {
    return SyntaxHighlighter.PROVIDER.create(fileType, project, file);
  }

  /**
   * Override this method to provide syntax highlighting (coloring) capabilities for your language implementation.
   * By syntax highlighting we mean highlighting of keywords, comments, braces etc. where lexing the file content is enough
   * to identify proper highlighting attributes.
   * <p/>
   * Default implementation doesn't highlight anything.
   * Requires read lock because some implementations may invoke operations that need it.
   * @param project     might be necessary to gather various project settings from.
   * @param virtualFile might be necessary to collect file specific settings
   * @return {@code SyntaxHighlighter} interface implementation for this particular language.
   */
  @RequiresReadLock(generateAssertion = false)
  public abstract @NotNull SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile);
}