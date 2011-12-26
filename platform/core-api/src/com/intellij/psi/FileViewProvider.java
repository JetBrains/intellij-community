/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
 *
 * @see com.intellij.psi.PsiFile#getViewProvider()
 * @see PsiManager#findViewProvider(com.intellij.openapi.vfs.VirtualFile)
 */
public interface FileViewProvider extends Cloneable, UserDataHolder {
  @NotNull PsiManager getManager();

  @Nullable Document getDocument();
  @NotNull CharSequence getContents();
  @NotNull VirtualFile getVirtualFile();

  @NotNull Language getBaseLanguage();

  /**
   * @return all languages this file supports. See {@link #getPsi(com.intellij.lang.Language)}
   */
  @NotNull Set<Language> getLanguages();

  /**
   * @param target target language
   * @return PsiFile for given language, or <code>null</code> if the language not present
   */
  PsiFile getPsi(@NotNull Language target);

  @NotNull List<PsiFile> getAllFiles();

  boolean isEventSystemEnabled();
  boolean isPhysical();

  long getModificationStamp();

  boolean supportsIncrementalReparse(Language rootLanguage);

  void rootChanged(PsiFile psiFile);
  void beforeContentsSynchronized();
  void contentsSynchronized();
  FileViewProvider clone();

  @Nullable
  PsiElement findElementAt(final int offset);
  @Nullable
  PsiReference findReferenceAt(final int offset);

  @Nullable
  PsiElement findElementAt(final int offset, final Language language);

  @Nullable
  PsiElement findElementAt(int offset, Class<? extends Language> lang);
  @Nullable
  PsiReference findReferenceAt(final int offsetInElement, @NotNull Language language);

  @NotNull
  FileViewProvider createCopy(VirtualFile copy);
}
