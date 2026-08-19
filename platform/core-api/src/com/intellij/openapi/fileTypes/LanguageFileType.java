// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * File type that specifies a {@link Language}, i.e. the file type of files that are parsed into a PSI tree
 * and get highlighting, completion, refactorings, and the rest of the language support built on top of it.
 *
 * <p>
 * Register it via the {@code com.intellij.fileType} extension point, and always declare the language ID there as well:
 * <pre>{@code
 * <fileType name="Python" language="Python" extensions="py;pyw"
 *           implementationClass="com.jetbrains.python.PythonFileType" fieldName="INSTANCE"/>
 * }</pre>
 * The declared {@code language} lets the platform answer {@link Language#getAssociatedFileType()} without loading the
 * implementation class; if it disagrees with {@link #getLanguage()}, the registration is reported as an error.
 *
 * <p>
 * A language should have exactly one primary file type. A file type that merely reuses the language of another one must be
 * {@linkplain #isSecondary() secondary} and must not declare the language in the XML.
 *
 * <p>
 * The mapping declared here is static: it says which language the files <i>of this type</i> are written in. The language of one
 * particular file &mdash; depending on its location, the project configuration, and so on &mdash; can still be overridden by a
 * {@link com.intellij.psi.LanguageSubstitutor}.
 *
 * @see com.intellij.psi.LanguageSubstitutor
 * @see com.intellij.openapi.fileTypes.impl.FileTypeBean for documentation of &lt;fileType .../&gt; tags.
 */
public abstract class LanguageFileType implements FileType {
  private final Language myLanguage;
  private final boolean mySecondary;

  /**
   * Creates a language file type for the specified language.
   *
   * @param language The language used in the files of the type.
   */
  protected LanguageFileType(@NotNull Language language) {
    this(language, false);
  }

  /**
   * Creates a language file type for the specified language.
   *
   * @param language  The language used in the files of the type.
   * @param secondary If true, this language file type will never be returned as the associated file type for the language.
   *                  (Used when a file type is reusing the language of another file type, e.g. XML).
   * @see #isSecondary()
   */
  protected LanguageFileType(@NotNull Language language, boolean secondary) {
    // passing Language instead of lazy resolve on getLanguage call (like LazyRunConfigurationProducer), is ok because:
    // 1. Usage of FileType nearly always requires Language
    // 2. FileType is created only on demand (if deprecated FileTypeFactory is not used).
    myLanguage = language;
    mySecondary = secondary;
    if (getClass().isAnonymousClass()) {
      throw new IllegalStateException("Must not create a Language from an anonymous implementation. " +
                                      "Use a separate class and register it in the plugin.xml to create a singleton instead. " +
                                      "Class: " + getClass());
    }
  }

  /**
   * Returns the language used in the files of the type.
   * <p>
   * This is the language of the file type as declared, which is not necessarily the language of a given file of this type:
   * to get the latter, use {@link com.intellij.psi.PsiFile#getLanguage()}, which takes
   * {@linkplain com.intellij.psi.LanguageSubstitutor substitutors} into account.
   *
   * @return The language instance.
   */
  public final @NotNull Language getLanguage() {
    return myLanguage;
  }

  @Override
  public final boolean isBinary() {
    return computeBinary();
  }

  /**
   * Remote development only.
   * Allows overriding {@code isBinary} when {@link LanguageFileType} is used
   * as a placeholder for unsupported file types on the frontend.
   */
  @ApiStatus.Internal
  protected boolean computeBinary() {
    return false;
  }

  /**
   * If true, this language file type will never be returned as the associated file type for the language.
   * (Used when a file type is reusing the language of another file type, e.g. XML).
   * <p>
   * Several file types may share one language &mdash; think of the many XML-based formats that are all parsed as XML &mdash;
   * but {@link Language#getAssociatedFileType()} has to pick a single one. Marking all but the owner of the language as
   * secondary is what makes that choice unambiguous.
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

  /**
   * Override this for languages that declare their own encoding inside the file itself, e.g. the {@code encoding} attribute of an
   * XML prolog or a {@code # coding:} line in Python; the returned charset wins over the encoding configured for the file.
   * <p>
   * Callers must use {@link CharsetUtil#extractCharsetFromFileContent(Project, VirtualFile, FileType, CharSequence)} instead of
   * calling this directly: it skips file types that do not override this method, thus avoiding useless work for most files.
   *
   * @param content the file content, already decoded into text.
   * @return the charset declared in the content, or {@code null} if there is none.
   */
  public Charset extractCharsetFromFileContent(@Nullable Project project, @Nullable VirtualFile file, @NotNull CharSequence content) {
    return extractCharsetFromFileContent(project, file, content.toString());
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return myLanguage.getDisplayName();
  }
}
