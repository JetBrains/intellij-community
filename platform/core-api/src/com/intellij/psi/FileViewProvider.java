/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Manages language-specific access to PSI for a single file.
 * <p/>
 * Custom providers are registered via {@link FileViewProviderFactory}.
 * <p/>
 * Please see <a href="http://confluence.jetbrains.net/display/IDEADEV/IntelliJ+IDEA+Architectural+Overview">IntelliJ IDEA Architectural Overview </a>
 * for high-level overview.
 *
 * @see com.intellij.psi.PsiFile#getViewProvider()
 * @see PsiManager#findViewProvider(com.intellij.openapi.vfs.VirtualFile)
 */
public interface FileViewProvider extends Cloneable, UserDataHolder {
  @NotNull
  PsiManager getManager();

  @Nullable
  Document getDocument();

  @NotNull
  CharSequence getContents();

  @NotNull
  VirtualFile getVirtualFile();

  @NotNull
  Language getBaseLanguage();

  /**
   * @return all languages this file supports. See {@link #getPsi(com.intellij.lang.Language)}
   */
  @NotNull
  Set<Language> getLanguages();

  /**
   * @param target target language
   * @return PsiFile for given language, or <code>null</code> if the language not present
   */
  PsiFile getPsi(@NotNull Language target);

  @NotNull
  List<PsiFile> getAllFiles();

  boolean isEventSystemEnabled();

  boolean isPhysical();

  long getModificationStamp();

  boolean supportsIncrementalReparse(@NotNull Language rootLanguage);

  void rootChanged(@NotNull PsiFile psiFile);

  void beforeContentsSynchronized();

  void contentsSynchronized();

  FileViewProvider clone();

  @Nullable
  PsiElement findElementAt(int offset);

  @Nullable
  PsiReference findReferenceAt(int offset);

  @Nullable
  PsiElement findElementAt(int offset, @NotNull Language language);

  @Nullable
  PsiElement findElementAt(int offset, @NotNull Class<? extends Language> lang);

  @Nullable
  PsiReference findReferenceAt(int offsetInElement, @NotNull Language language);

  @NotNull
  FileViewProvider createCopy(@NotNull VirtualFile copy);

  @NotNull
  PsiFile getStubBindingRoot();
}
