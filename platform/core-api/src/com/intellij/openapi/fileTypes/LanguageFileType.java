/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  @Override
  public final boolean isBinary() {
    return false;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, @NotNull final byte[] content) {
    return null;
  }

  /**
   * @deprecated implement own {@link com.intellij.debugger.engine.JavaDebugAware} instead
   */
  @Deprecated
  public boolean isJVMDebuggingSupported() {
    return false;
  }

  /**
   * Callers: use {@link CharsetUtil#extractCharsetFromFileContent(Project, VirtualFile, FileType, CharSequence)}
   * Overriders: override {@link #extractCharsetFromFileContent(Project, VirtualFile, CharSequence)} instead
   * @deprecated 
   */
  public Charset extractCharsetFromFileContent(@Nullable Project project, @Nullable VirtualFile file, @NotNull String content) {
    return null;
  }
  
  public Charset extractCharsetFromFileContent(@Nullable Project project, @Nullable VirtualFile file, @NotNull CharSequence content) {
    //noinspection deprecation
    return extractCharsetFromFileContent(project, file, content.toString());
  }
}
