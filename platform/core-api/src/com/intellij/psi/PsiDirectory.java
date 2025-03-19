// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Represents a file system directory and allows accessing its contents.
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
  @Override
  @NotNull
  VirtualFile getVirtualFile();

  @Override
  @NotNull
  String getName();

  @Override
  @NotNull
  PsiElement setName(@NotNull String name) throws IncorrectOperationException;

  /**
   * Returns the parent directory of the directory.
   *
   * @return the parent directory, or null if the directory has no parent.
   */
  @Nullable
  PsiDirectory getParentDirectory();

  @Override
  @Nullable
  PsiDirectory getParent();

  /**
   * Returns the list of subdirectories of this directory.
   *
   * @return the array of subdirectories.
   */
  PsiDirectory @NotNull [] getSubdirectories();

  /**
   * Returns the list of files in the directory.
   *
   * @return the array of files.
   */
  PsiFile @NotNull [] getFiles();

  /**
   * Returns the list of files in the directory which are contained in the given {@param scope}.
   *
   * @param scope the scope which filters the resulting files.
   * @return the array of files.
   */
  default PsiFile @NotNull [] getFiles(@NotNull GlobalSearchScope scope) {
    PsiFile[] result = Arrays.stream(getFiles()).filter(psiFile -> scope.contains(psiFile.getVirtualFile())).toArray(PsiFile[]::new);
    return result.length == 0 ? PsiFile.EMPTY_ARRAY : result;
  }

  /**
   * Finds the subdirectory of this directory with the specified name.
   *
   * @param name the name of the subdirectory to find.
   * @return the subdirectory instance, or null if no subdirectory with such a name is found.
   */
  @Nullable
  PsiDirectory findSubdirectory(@NonNls @NotNull String name);

  /**
   * Finds a file with the specified name in this directory.
   *
   * @param name the name of the file to find.
   * @return the file instance, or null if no file with such a name is found.
   */
  @Nullable
  PsiFile findFile(@NotNull @NonNls String name);

  /**
   * Creates a subdirectory with the specified name in the directory.
   *
   * @param name the name of the subdirectory to create.
   * @return the created directory instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  @NotNull
  PsiDirectory createSubdirectory(@NotNull String name) throws IncorrectOperationException;

  /**
   * Checks if it's possible to create a subdirectory with the specified name in the directory,
   * and throws an exception if the creation is not possible. Does not actually modify
   * anything.
   *
   * @param name the name of the subdirectory to check creation possibility.
   * @throws IncorrectOperationException if the creation is not possible.
   */
  void checkCreateSubdirectory(@NotNull String name) throws IncorrectOperationException;

  /**
   * Creates a file with the specified name in the directory.
   *
   * @param name the name of the file to create.
   * @return the created file instance.
   * @throws IncorrectOperationException if the operation failed for some reason.
   */
  @NotNull PsiFile createFile(@NotNull @NonNls String name) throws IncorrectOperationException;

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
}
