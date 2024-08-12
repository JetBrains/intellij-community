// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.StringTokenizer;

public final class DirectoryUtil {
  private DirectoryUtil() {
  }


  /**
   * Creates the directory with the given path via PSI, including any
   * necessary but nonexistent parent directories. Must be run in write action.
   * @param path directory path in the local file system; separators must be '/'
   * @return psiDirectory if path exists or has been created as the result of this method call; {@code null} otherwise
   */
  public static PsiDirectory mkdirs(PsiManager manager, String path) throws IncorrectOperationException{
    if (File.separatorChar != '/') {
      if (path.indexOf(File.separatorChar) != -1) {
        throw new IllegalArgumentException("separators must be '/'; path is " + path);
      }
    }

    @Nullable Pair<PsiDirectory, String> pair = findLongestExistingDirectoryAndItsPath(manager, path);
    if (pair == null) {
      return null;
    }
    PsiDirectory directory = pair.first;
    String existingPath = pair.second;
    
    if (path.equals(existingPath)) {
      return directory;
    }

    String postfix = path.substring(existingPath.length() + 1);
    StringTokenizer tokenizer = new StringTokenizer(postfix, "/");
    while (tokenizer.hasMoreTokens()) {
      directory = directory.createSubdirectory(tokenizer.nextToken());
    }

    return directory;
  }

  /**
   * @param path directory path in the local file system; separators must be '/'
   * @return the inner most existing directory along the given <code>path</code>
   */
  public static @Nullable PsiDirectory findLongestExistingDirectory(@NotNull PsiManager manager, @NotNull String path) {
    Pair<PsiDirectory, String> pair = findLongestExistingDirectoryAndItsPath(manager, path);
    return pair != null ? pair.first : null;
  }

  /**
   * virtualFile.getPath() is not guaranteed to be the same as path by which virtualFile was found, e.g. on case insensitive OS
   * thus returning the path by which file was found
   */
  private static @Nullable Pair<PsiDirectory, String> findLongestExistingDirectoryAndItsPath(@NotNull PsiManager manager, @NotNull String path) {
    PsiDirectory directory = null;
    // find longest existing path
    while (path.length() > 0) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
      if (file != null) {
        directory = manager.findDirectory(file);
        if (directory == null) {
          return null;
        }
        break;
      }

      if (StringUtil.endsWithChar(path, '/')) {
        path = path.substring(0, path.length() - 1);
        if (SystemInfo.isWindows && path.length() == 2 && OSAgnosticPathUtil.startsWithWindowsDrive(path)) {
          return null;
        }
      }

      int index = path.lastIndexOf('/');
      if (index == -1) {
        // nothing to do more
        return null;
      }

      path = path.substring(0, index);
    }
    return Pair.create(directory, path);
  }
  
  public static PsiDirectory createSubdirectories(final String subDirName, PsiDirectory baseDirectory, final String delim) throws IncorrectOperationException {
    StringTokenizer tokenizer = new StringTokenizer(subDirName, delim);
    PsiDirectory dir = baseDirectory;
    boolean firstToken = true;
    while (tokenizer.hasMoreTokens()) {
      String dirName = tokenizer.nextToken();
      if (tokenizer.hasMoreTokens()) {
        if (firstToken && "~".equals(dirName)) {
          final VirtualFile userHomeDir = VfsUtil.getUserHomeDir();
          if (userHomeDir == null) throw new IncorrectOperationException("User home directory not found");
          final PsiDirectory directory1 = baseDirectory.getManager().findDirectory(userHomeDir);
          if (directory1 == null) throw new IncorrectOperationException("User home directory not found");
          dir = directory1;
          continue;
        }
        else if ("..".equals(dirName)) {
          dir = dir.getParentDirectory();
          if (dir == null) throw new IncorrectOperationException("Not a valid directory");
          continue;
        }
        else if (".".equals(dirName)) {
          continue;
        }
        PsiDirectory existingDir = dir.findSubdirectory(dirName);
        if (existingDir != null) {
          dir = existingDir;
          continue;
        }
      }
      dir = dir.createSubdirectory(dirName);
      firstToken = false;
    }
    return dir;
  }
}
