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
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * Kind of file types capable to provide {@link Language}.
 */
public abstract class LanguageFileType implements FileType{
  private final Language myLanguage;

  /**
   * Creates a language file type for the specified language.
   * @param language The language used in the files of the type.
   */
  protected LanguageFileType(@NotNull final Language language) {
    myLanguage = language;
  }

  /**
   * Returns the language used in the files of the type.
   * @return The language instance.
   */

  @NotNull
  public final Language getLanguage() {
    return myLanguage;
  }

  /**
   * Lower level API for customizing language's file syntax highlighting in editor component.
   * @param project The project in which the highligher will work, or null if the highlighter is not tied to any project.
   * @param virtualFile The file to be highlighted
   * @param colors color scheme highlighter shall be initialized with.
   * @return EditorHiglighter implementation
   */
  public EditorHighlighter getEditorHighlighter(@Nullable Project project,
                                                @Nullable final VirtualFile virtualFile,
                                                @NotNull EditorColorsScheme colors) {
    return EditorHighlighterFactory.getInstance().createEditorHighlighter(SyntaxHighlighter.PROVIDER.create(this, project, virtualFile), colors);
  }

  public final boolean isBinary() {
    return false;
  }

  public final boolean isReadOnly() {
    return false;
  }

  public String getCharset(@NotNull VirtualFile file, final byte[] content) {
    return null;
  }

  public boolean isJVMDebuggingSupported() {
    return false;
  }

  public Charset extractCharsetFromFileContent(@Nullable Project project, @Nullable VirtualFile file, @NotNull String content) {
    return null;
  }
}
