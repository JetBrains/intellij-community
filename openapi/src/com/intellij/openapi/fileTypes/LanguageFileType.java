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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Kind of file types capable to provide {@link Language}.
 */
public abstract class LanguageFileType implements FileType{
  private Language myLanguage;

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
  public final SyntaxHighlighter getHighlighter(@Nullable Project project, @Nullable final VirtualFile virtualFile) {
    return myLanguage.getSyntaxHighlighter(project, virtualFile);
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

  public String getCharset(VirtualFile file) {
    return null;
  }

  public boolean isJVMDebuggingSupported() {
    return false;
  }
}
