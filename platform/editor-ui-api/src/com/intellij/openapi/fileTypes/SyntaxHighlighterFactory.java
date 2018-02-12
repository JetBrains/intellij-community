/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public abstract class SyntaxHighlighterFactory {
  public static final SyntaxHighlighterLanguageFactory LANGUAGE_FACTORY = new SyntaxHighlighterLanguageFactory();

  /**
   * Returns syntax highlighter for the given language.
   *
   * @param language a {@code Language} to get highlighter for
   * @param project  might be necessary to gather various project settings from
   * @param file     might be necessary to collect file specific settings
   * @return {@code SyntaxHighlighter} interface implementation for the given file type
   */
  public static SyntaxHighlighter getSyntaxHighlighter(@NotNull Language language, @Nullable Project project, @Nullable VirtualFile file) {
    return LANGUAGE_FACTORY.forLanguage(language).getSyntaxHighlighter(project, file);
  }

  /**
   * Returns syntax highlighter for the given file type.
   * Note: it is recommended to use {@link #getSyntaxHighlighter(Language, Project, VirtualFile)} in most cases,
   * and use this method only when you are do not know the language you use.
   *
   * @param fileType a file type to use to select appropriate highlighter
   * @param project  might be necessary to gather various project settings from
   * @param file     might be necessary to collect file specific settings
   * @return {@code SyntaxHighlighter} interface implementation for the given file type
   */
  @Nullable
  public static SyntaxHighlighter getSyntaxHighlighter(@NotNull FileType fileType, @Nullable Project project, @Nullable VirtualFile file) {
    return SyntaxHighlighter.PROVIDER.create(fileType, project, file);
  }

  /**
   * Override this method to provide syntax highlighting (coloring) capabilities for your language implementation.
   * By syntax highlighting we mean highlighting of keywords, comments, braces etc. where lexing the file content is enough
   * to identify proper highlighting attributes.
   * <p/>
   * Default implementation doesn't highlight anything.
   *
   * @param project     might be necessary to gather various project settings from.
   * @param virtualFile might be necessary to collect file specific settings
   * @return {@code SyntaxHighlighter} interface implementation for this particular language.
   */
  @NotNull
  public abstract SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile);
}