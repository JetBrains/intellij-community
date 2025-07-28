// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
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
  protected LanguageFileType(@NotNull Language language) {
    this(language, false);
  }

  /**
   * Creates a language file type for the specified language.
   * @param language The language used in the files of the type.
   * @param secondary If true, this language file type will never be returned as the associated file type for the language.
   *                  (Used when a file type is reusing the language of another file type, e.g. XML).
   */
  protected LanguageFileType(@NotNull Language language, boolean secondary) {
    // passing Language instead of lazy resolve on getLanguage call (like LazyRunConfigurationProducer), is ok because:
    // 1. Usage of FileType nearly always requires Language
    // 2. FileType is created only on demand (if deprecated FileTypeFactory is not used).
    myLanguage = language;
    mySecondary = secondary;
    if (getClass().isAnonymousClass()) {
      throw new IllegalStateException("Must not create a Language from an anonymous implementation. Use a separate class and register it in the plugin.xml to create a singleton instead. Class: "+getClass());
    }
  }

  /**
   * Returns the language used in the files of the type.
   * @return The language instance.
   */
  public final @NotNull Language getLanguage() {
    return myLanguage;
  }

  @Override
  public final boolean isBinary() {
    return false;
  }

  /**
   * If true, this language file type will never be returned as the associated file type for the language.
   * (Used when a file type is reusing the language of another file type, e.g. XML).
   */
  public boolean isSecondary() {
    return mySecondary;
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

  @Override
  public @Nls @NotNull String getDisplayName() {
    return myLanguage.getDisplayName();
  }
}
