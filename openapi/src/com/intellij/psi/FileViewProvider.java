/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface FileViewProvider extends Cloneable{
  PsiManager getManager();

  @Nullable Document getDocument();
  @NotNull CharSequence getContents();
  @NotNull VirtualFile getVirtualFile();

  Language getBaseLanguage();
  Set<Language> getRelevantLanguages();
  Set<Language> getPrimaryLanguages();
  PsiFile getPsi(Language target);

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
  PsiReference findReferenceAt(final int offsetInElement, final Language language);
  Lexer createLexer(final Language language);

  boolean isLockedByPsiOperations();
}
