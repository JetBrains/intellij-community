/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.navigation.NavigationItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public interface PsiDirectory extends PsiElement, PsiFileSystemItem, NavigationItem {
  PsiDirectory[] EMPTY_ARRAY = new PsiDirectory[0];

  @NotNull
  VirtualFile getVirtualFile();

  String getName();
  @NotNull
  PsiElement setName(String name) throws IncorrectOperationException;

  @Nullable
  PsiPackage getPackage();

  @Nullable
  PsiDirectory getParentDirectory();

  @NotNull
  PsiDirectory[] getSubdirectories();

  @NotNull
  PsiFile[] getFiles();

  @NotNull
  PsiClass[] getClasses();

  @Nullable
  PsiDirectory findSubdirectory(String name);

  @Nullable
  PsiFile findFile(String name);

  @NotNull PsiClass createClass(String name) throws IncorrectOperationException;
  void checkCreateClass(String name) throws IncorrectOperationException;
  @NotNull PsiClass createInterface(String name) throws IncorrectOperationException;
  void checkCreateInterface(String name) throws IncorrectOperationException;
  @NotNull PsiClass createEnum(String name) throws IncorrectOperationException;
  @NotNull PsiClass createAnnotationType(String name) throws IncorrectOperationException;
  @NotNull PsiDirectory createSubdirectory(String name) throws IncorrectOperationException;
  void checkCreateSubdirectory(String name) throws IncorrectOperationException;
  @NotNull PsiFile createFile(String name) throws IncorrectOperationException;
  void checkCreateFile(String name) throws IncorrectOperationException;

  boolean isSourceRoot();
}
