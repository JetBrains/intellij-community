/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IncorrectOperationException;
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
