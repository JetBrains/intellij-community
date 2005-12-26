/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;

import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public interface FileViewProvider extends Cloneable{
  PsiManager getManager();

  @Nullable Document getDocument();
  @NotNull CharSequence getContents();
  @NotNull VirtualFile getVirtualFile();

  Language getBaseLanguage();
  Set<Language> getRelevantLanguages();
  PsiFile getPsi(Language target);

  boolean isEventSystemEnabled();
  boolean isPhysical();

  long getModificationStamp();

  void rootChanged(PsiFile psiFile);
  void contentsChanged();
  FileViewProvider clone();
}
