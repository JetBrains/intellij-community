// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Set;

/**
 * Manages language-specific access to PSI for a single file.
 * <p/>
 * Custom providers are registered via {@link FileViewProviderFactory}.
 * <p/>
 * Please see <a href="https://plugins.jetbrains.com/docs/intellij/file-view-providers.html">File View Providers</a>
 * for high-level overview.
 *
 * @see PsiFile#getViewProvider()
 * @see PsiManager#findViewProvider(VirtualFile)
 */
public interface FileViewProvider extends Cloneable, UserDataHolderEx {

  /**
   * @return this project's PsiManager
   */
  @NotNull
  PsiManager getManager();

  /**
   * @return the document corresponding to this file. Can be {@code null} for binary or files without events enabled.
   * @see FileType#isBinary()
   * @see PsiBinaryFile
   * @see #isEventSystemEnabled()
   */
  Document getDocument();

  /**
   * @return the contents of this file view provider, which are parsed into PSI trees. May or may not be equal
   * to the contents of the corresponding document. The latter happens for non-committed documents.
   * If the document is modified but not yet committed, the result is equivalent to {@link PsiDocumentManager#getLastCommittedText(Document)}.
   * @see #getDocument()
   * @see PsiDocumentManager#isUncommited(Document)
   */
  @NotNull
  CharSequence getContents();

  /**
   * @return the virtual file corresponding to this view provider. Physical or an instance of {@link LightVirtualFile} for most non-physical files.
   */
  @NotNull
  VirtualFile getVirtualFile();

  /**
   * @return the language of the main PSI tree (or the only one in a single-tree view providers). Used when returning a PsiFile from
   * {@link PsiManager#findFile(VirtualFile)},
   * {@link PsiDocumentManager#getPsiFile(Document)} etc.
   */
  @NotNull
  Language getBaseLanguage();

  /**
   * @return all languages this file supports, in no particular order.
   * @see #getPsi(Language)
   */
  @NotNull
  @Unmodifiable
  Set<Language> getLanguages();

  /**
   * Check if the given language is supported.
   * Implementations may provide a more effective way to check without getting all languages.
   */
  default boolean hasLanguage(@NotNull Language language) {
    return getLanguages().contains(language);
  }

  /**
   * @return PsiFile for given language, or {@code null} if the language is not present
   */
  PsiFile getPsi(@NotNull Language target);

  /**
   * @return all PSI files for this view provider. In most cases, just one main file. For multi-root languages, several files. The files' languages
   * should be the same as {@link #getLanguages()}. The main file which corresponds to {@link #getBaseLanguage()}, should be the first one.
   * Otherwise, the order is non-deterministic and should not be relied upon.
   */
  @NotNull
  @Unmodifiable
  List<@NotNull PsiFile> getAllFiles();

  /**
   * @return whether PSI events are fired when changes occur inside PSI in this view provider. {@code true} for physical files and for some non-physical as well.
   * @see PsiTreeChangeListener
   * @see PsiFileFactory#createFileFromText(String, FileType, CharSequence, long, boolean)
   * @see PsiFile#isPhysical()
   */
  boolean isEventSystemEnabled();

  /**
   * @return whether this file corresponds to a file on a disk. For such files, {@link PsiFile#getVirtualFile()} returns non-null.
   * Not to be confused with {@link PsiFile#isPhysical()} which (for historical reasons) returns {@code getViewProvider().isEventSystemEnabled()}
   * @see #isEventSystemEnabled()
   * @see PsiFile#isPhysical()
   */
  boolean isPhysical();

  /**
   * @return a number to quickly check if contents of this view provider have diverged from the corresponding {@link VirtualFile} or {@link Document}.
   * If a document is modified but not yet committed, the result is the same as {@link PsiDocumentManager#getLastCommittedStamp(Document)}
   * @see VirtualFile#getModificationStamp()
   * @see Document#getModificationStamp()
   */
  long getModificationStamp();

  /**
   * @return whether the PSI file with the specified root language supports incremental reparse.
   * @see #getLanguages()
   */
  boolean supportsIncrementalReparse(@NotNull Language rootLanguage);

  /**
   * Invoked when any PSI change happens in any of the PSI files corresponding to this view provider.
   *
   * @param psiFile the file where PSI has just been changed.
   */
  void rootChanged(@NotNull PsiFile psiFile);

  /**
   * Invoked before document or VFS changes are processed that affect PSI inside the corresponding file.
   */
  void beforeContentsSynchronized();

  /**
   * Invoked after PSI in the corresponding file is synchronized with the corresponding document, which can happen
   * after VFS, document or PSI changes.<p/>
   * <p>
   * Multi-language file view providers may override this method to recalculate template data languages.
   *
   * @see #getLanguages()
   */
  void contentsSynchronized();

  /**
   * @return a copy of this view provider, built on a {@link LightVirtualFile}, not physical and with PSI events disabled.
   * @see #isPhysical()
   * @see #isEventSystemEnabled()
   * @see #createCopy(VirtualFile)
   */
  FileViewProvider clone();

  /**
   * @return the deepest (leaf) PSI element in the main PSI tree at the specified offset.
   * @see #getBaseLanguage()
   * @see #findElementAt(int, Class)
   * @see #findElementAt(int, Language)
   * @see PsiFile#findElementAt(int)
   */
  @Nullable
  PsiElement findElementAt(int offset);

  /**
   * @return a reference in the main PSI tree at the specified offset.
   * @see #getBaseLanguage()
   * @see PsiFile#findReferenceAt(int)
   * @see #findReferenceAt(int, Language)
   */
  @Nullable
  PsiReference findReferenceAt(int offset);

  /**
   * @return the deepest (leaf) PSI element in the PSI tree with the specified root language at the specified offset.
   * @see #getBaseLanguage()
   * @see #findElementAt(int)
   */
  @Nullable
  PsiElement findElementAt(int offset, @NotNull Language language);

  /**
   * @return the deepest (leaf) PSI element in the PSI tree with the specified root language class at the specified offset.
   * @see #getBaseLanguage()
   * @see #findElementAt(int)
   */
  @Nullable
  PsiElement findElementAt(int offset, @NotNull Class<? extends Language> lang);

  /**
   * @return a reference in the PSI tree with the specified root language at the specified offset.
   * @see #getBaseLanguage()
   * @see PsiFile#findReferenceAt(int)
   * @see #findReferenceAt(int)
   */
  @Nullable
  PsiReference findReferenceAt(int offset, @NotNull Language language);

  /**
   * Creates a copy of this view provider linked with the given (typically light) file.
   * The result provider is required to be NOT event-system-enabled.
   *
   * @see LightVirtualFile
   * @see #isEventSystemEnabled()
   */
  @NotNull
  FileViewProvider createCopy(@NotNull VirtualFile copy);

  /**
   * @return the PSI root for which stubs are to be built if supported. By default, it's the main root.
   * @see #getBaseLanguage()
   */
  @NotNull
  PsiFile getStubBindingRoot();

  /**
   * @return the same as {@code getVirtualFile().getFileType()}, but cached.
   */
  @NotNull
  FileType getFileType();
}
