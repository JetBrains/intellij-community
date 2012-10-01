/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SyntaxHighlighterFactory {
  public static final SyntaxHighlighterLanguageFactory LANGUAGE_FACTORY = new SyntaxHighlighterLanguageFactory();
  private static final NotNullLazyValue<SyntaxHighlighterProvider> PROVIDER = new NotNullLazyValue<SyntaxHighlighterProvider>() {
    @NotNull
    @Override
    protected SyntaxHighlighterProvider compute() {
      return new FileTypeExtensionFactory<SyntaxHighlighterProvider>(SyntaxHighlighterProvider.class, "com.intellij.syntaxHighlighter").get();
    }
  };

  public static SyntaxHighlighter getSyntaxHighlighter(Language lang, Project project, VirtualFile virtualFile) {
    return LANGUAGE_FACTORY.forLanguage(lang).getSyntaxHighlighter(project, virtualFile);
  }

  @Nullable
  public static SyntaxHighlighter getSyntaxHighlighter(final FileType fileType, final @Nullable Project project, final @Nullable VirtualFile virtualFile) {
    return PROVIDER.getValue().create(fileType, project, virtualFile);
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