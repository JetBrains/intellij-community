/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IncorrectOperationException;

/**
 *
 */
public interface PsiDirectory extends PsiElement, PsiNamedElement {
  PsiDirectory[] EMPTY_ARRAY = new PsiDirectory[0];

  VirtualFile getVirtualFile();

  String getName();
  PsiElement setName(String name) throws IncorrectOperationException;
  void checkSetName(String name) throws IncorrectOperationException;

  PsiPackage getPackage();

  PsiDirectory getParentDirectory();
  PsiDirectory[] getSubdirectories();
  PsiFile[] getFiles();
  PsiClass[] getClasses();

  PsiDirectory findSubdirectory(String name);
  PsiFile findFile(String name);

  PsiClass createClass(String name) throws IncorrectOperationException;
  void checkCreateClass(String name) throws IncorrectOperationException;
  PsiClass createInterface(String name) throws IncorrectOperationException;
  void checkCreateInterface(String name) throws IncorrectOperationException;
  PsiClass createEnum(String name) throws IncorrectOperationException;
  PsiClass createAnnotationType(String name) throws IncorrectOperationException;
  PsiDirectory createSubdirectory(String name) throws IncorrectOperationException;
  void checkCreateSubdirectory(String name) throws IncorrectOperationException;
  PsiFile createFile(String name) throws IncorrectOperationException;
  void checkCreateFile(String name) throws IncorrectOperationException;

  boolean isSourceRoot();
}
