// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * Kind of file types capable to provide {@link Language}.
 * Note that the associated language can still be overridden by a {@link com.intellij.psi.LanguageSubstitutor}.
 */
public abstract class LanguageFileType implements FileType {
  private final Language myLanguage;
  private final boolean mySecondary;

  /**
   * Creates a language file type for the specified language.
   * @param language The language used in the files of the type.
   */
  protected LanguageFileType(@Nullable Language language) {
    this(language, false);
  }

  /**
   * Creates a language file type for the specified language.
   * @param language The language used in the files of the type.
   * @param secondary If true, this language file type will never be returned as the associated file type for the language.
   *                  (Used when a file type is reusing the language of another file type, e.g. XML).
   */
  protected LanguageFileType(@Nullable Language language, boolean secondary) {
    myLanguage = language;
    mySecondary = secondary;
  }

  /**
   * Returns the language used in the files of the type.
   * @return The language instance.
   */
  @NotNull
  public Language getLanguage() {
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

  /**
   * If true, this language file type will never be returned as the associated file type for the language.
   * (Used when a file type is reusing the language of another file type, e.g. XML).
   */
  public boolean isSecondary() {
    return mySecondary;
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
   * @deprecated Callers: use {@link CharsetUtil#extractCharsetFromFileContent(Project, VirtualFile, FileType, CharSequence)}
   * Overriders: override {@link #extractCharsetFromFileContent(Project, VirtualFile, CharSequence)} instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public Charset extractCharsetFromFileContent(@Nullable Project project, @Nullable VirtualFile file, @NotNull String content) {
    return null;
  }

  public Charset extractCharsetFromFileContent(@Nullable Project project, @Nullable VirtualFile file, @NotNull CharSequence content) {
    return extractCharsetFromFileContent(project, file, content.toString());
  }
}
