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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a file system directory and allows to access its contents.
 */
public interface PsiDirectory extends PsiFileSystemItem {
  /**
   * The empty array of PSI directories which can be reused to avoid unnecessary allocations.
   */
  PsiDirectory[] EMPTY_ARRAY = new PsiDirectory[0];

  /**
   * Returns the virtual file represented by the PSI directory.
   *
   * @return the virtual file instance.
   */
  @NotNull
  VirtualFile getVirtualFile();

  String getName();
  @NotNull
  PsiElement setName(@NotNull String name) throws IncorrectOperationException;

  /**
   * Returns the package corresponding to the directory.
   *
   * @return the package instance, or null if the directory does not correspond to any package.
   */
  @Nullable
  PsiPackage getPackage();

  /**
   * Returns the parent directory of the directory.
   *
   * @return the parent directory, or null if the directory has no parent.
   */
  @Nullable
  PsiDirectory getParentDirectory();

  @Nullable
  PsiDirectory getParent();

  /**
   * Returns the list of subdirectories of this directory.
   *
   * @return the array of subdirectories.
   */
  @NotNull
  PsiDirectory[] getSubdirectories();

  /**
   * Returns the list of files in the directory.
   *
   * @return the array of files.
   */
  @NotNull
  PsiFile[] getFiles();

  /**
   * Returns the list of Java classes contained in the directory.
   *
   * @return the array of classes.
   */
  @NotNull
  PsiClass[] getClasses();

  /**
   * Finds the subdirectory of this directory with the specified name.
   *
   * @param name the name of the subdirectory to find.
   * @return the subdirectory instance, or null if no subdirectory with such a name is found.
   */
  @Nullable
  PsiDirectory findSubdirectory(String name);

  /**
   * Finds a file with the specified name in this directory.
   *
   * @param name the name of the file to find.
   * @return the file instance, or null if no file with such a name is found.
   */
  @Nullable
  PsiFile findFile(@NonNls String name);

  /**
   * Creates a class with the specified name in the directory.
   *
   * @param name the name of the class to create (not including the file extension).
   * @return the created class instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  @NotNull PsiClass createClass(String name) throws IncorrectOperationException;

  /**
   * Creates a class with the specified name in the directory.
   *
   * @param name the name of the class to create (not including the file extension).
   * @param templateName custom file template to create class text based on.
   * @return the created class instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   * @since 5.1
   */
  @NotNull PsiClass createClass(String name, String templateName) throws IncorrectOperationException;

  /**
   * Checks if it's possible to create a class with the specified name in the directory,
   * and throws an exception if the creation is not possible. Does not actually modify
   * anything.
   *
   * @param name the name of the class to check creation possibility (not including the file extension).
   * @throws IncorrectOperationException if the creation is not possible.
   */
  void checkCreateClass(String name) throws IncorrectOperationException;

  /**
   * Creates an interface class with the specified name in the directory.
   *
   * @param name the name of the interface to create (not including the file extension).
   * @return the created interface instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  @NotNull PsiClass createInterface(String name) throws IncorrectOperationException;

  /**
   * Checks if it's possible to create an interface with the specified name in the directory,
   * and throws an exception if the creation is not possible. Does not actually modify
   * anything.
   *
   * @param name the name of the interface to check creation possibility (not including the file extension).
   * @throws IncorrectOperationException if the creation is not possible.
   */
  void checkCreateInterface(String name) throws IncorrectOperationException;

  /**
   * Creates an enumeration class with the specified name in the directory.
   *
   * @param name the name of the enumeration class to create (not including the file extension).
   * @return the created class instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  @NotNull PsiClass createEnum(String name) throws IncorrectOperationException;

  /**
   * Creates an annotation class with the specified name in the directory.
   *
   * @param name the name of the annotation class to create (not including the file extension).
   * @return the created class instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  @NotNull PsiClass createAnnotationType(String name) throws IncorrectOperationException;

  /**
   * Creates a subdirectory with the specified name in the directory.
   *
   * @param name the name of the subdirectory to create.
   * @return the created directory instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  @NotNull PsiDirectory createSubdirectory(String name) throws IncorrectOperationException;

  /**
   * Checks if it's possible to create a subdirectory with the specified name in the directory,
   * and throws an exception if the creation is not possible. Does not actually modify
   * anything.
   *
   * @param name the name of the subdirectory to check creation possibility.
   * @throws IncorrectOperationException if the creation is not possible.
   */
  void checkCreateSubdirectory(String name) throws IncorrectOperationException;

  /**
   * Creates a file with the specified name in the directory.
   *
   * @param name the name of the file to create.
   * @return the created file instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  @NotNull PsiFile createFile(String name) throws IncorrectOperationException;

  @NotNull PsiFile copyFileFrom(@NotNull String newName, @NotNull PsiFile originalFile) throws IncorrectOperationException;

  /**
   * Checks if it's possible to create a file with the specified name in the directory,
   * and throws an exception if the creation is not possible. Does not actually modify
   * anything.
   *
   * @param name the name of the file to check creation possibility.
   * @throws IncorrectOperationException if the creation is not possible.
   */
  void checkCreateFile(@NotNull String name) throws IncorrectOperationException;

  /**
   * Checks if the directory is a source root for the project to which it belongs.
   *
   * @return true if the directory is a source root, false otherwise
   */
  boolean isSourceRoot();

  LanguageLevel getLanguageLevel();
}
