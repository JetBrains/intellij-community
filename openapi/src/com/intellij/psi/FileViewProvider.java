/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public interface FileViewProvider extends Cloneable, UserDataHolder {
  PsiManager getManager();

  @Nullable Document getDocument();
  @NotNull CharSequence getContents();
  @NotNull VirtualFile getVirtualFile();

  @NotNull Language getBaseLanguage();
  @NotNull Set<Language> getRelevantLanguages();
  @NotNull Set<Language> getPrimaryLanguages();
  PsiFile getPsi(@NotNull Language target);

  @NotNull List<PsiFile> getAllFiles();

  boolean isEventSystemEnabled();
  boolean isPhysical();

  long getModificationStamp();

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
  Lexer createLexer(@NotNull Language language);

  boolean isLockedByPsiOperations();
}
