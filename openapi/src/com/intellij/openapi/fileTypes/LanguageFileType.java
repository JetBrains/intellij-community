/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
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
    myLanguage.associateFileType(this);
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
   * Returns the syntax highlighter for the files of the type.
   * @param project The project in which the highligher will work, or null if the highlighter is not tied to any project.
   * @param virtualFile The file to be highlighted
   * @return The highlighter implementation.
   */

  @NotNull
  public SyntaxHighlighter getHighlighter(@Nullable Project project, @Nullable final VirtualFile virtualFile) {
    return myLanguage.getSyntaxHighlighter(project, virtualFile);
  }

  /**
   * Lower level API for customizing language's file syntax highlighting in editor component. Delegates to {@link #getHighlighter(com.intellij.openapi.project.Project,com.intellij.openapi.vfs.VirtualFile)} by default.
   * @param project The project in which the highligher will work, or null if the highlighter is not tied to any project.
   * @param virtualFile The file to be highlighted
   * @param colors color scheme highlighter shall be initialized with.
   * @return EditorHiglighter implementation
   */
  public EditorHighlighter getEditorHighlighter(@Nullable Project project,
                                                @Nullable final VirtualFile virtualFile,
                                                @NotNull EditorColorsScheme colors) {
    return PeerFactory.getInstance().createEditorHighlighter(getHighlighter(project, virtualFile), colors);
  }

  /**
   * Returns the structure view builder for the specified file.
   * 
   * @param file The file for which the structure view builder is requested.
   * @param project The project to which the file belongs.
   * @return The structure view builder, or null if no structure view is available for the file.
   */
  @Nullable
  public StructureViewBuilder getStructureViewBuilder(@NotNull VirtualFile file, @NotNull Project project) {
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    return psiFile == null ?  null : myLanguage.getStructureViewBuilder(psiFile);
  }

  public final boolean isBinary() {
    return false;
  }

  public final boolean isReadOnly() {
    return false;
  }

  public String getCharset(@NotNull VirtualFile file) {
    return null;
  }

  public boolean isJVMDebuggingSupported() {
    return false;
  }

  public Charset extractCharsetFromFileContent(@Nullable Project project, @NotNull VirtualFile file, @NotNull String content) {
    return null;
  }
}
