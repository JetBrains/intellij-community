// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * author: lesya
 */
public final class LibrariesHelperImpl extends LibrariesHelper {

  @Override
  public VirtualFile findJarByClass(Library library, @NonNls String fqn) {
    return library == null ? null : findRootByClass(Arrays.asList(library.getFiles(OrderRootType.CLASSES)), fqn);
  }

  @Override
  public @Nullable VirtualFile findRootByClass(@NotNull List<? extends VirtualFile> roots, String fqn) {
    for (VirtualFile file : roots) {
      if (findInFile(file, new StringTokenizer(fqn, "."))) return file;
    }
    return null;
  }

  @Override
  public boolean isClassAvailableInLibrary(Library library, String fqn) {
    final String[] urls = library.getUrls(OrderRootType.CLASSES);
    return isClassAvailable(urls, fqn);
  }

  @Override
  public boolean isClassAvailable(final String[] urls, String fqn) {
    for (String url : urls) {
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      if (file == null) continue;
      if (!(file.getFileSystem() instanceof JarFileSystem) && !file.isDirectory()) {
        file = JarFileSystem.getInstance().getJarRootForLocalFile(file);
      }
      if (file == null) continue;
      if (findInFile(file, new StringTokenizer(fqn, "."))) return true;
    }
    return false;
  }

  private static boolean findInFile(VirtualFile root, final StringTokenizer filePath) {
    if (!filePath.hasMoreTokens()) return true;
    @NonNls String name = filePath.nextToken();
    if (!filePath.hasMoreTokens()) {
      name += ".class";
    }
    final VirtualFile child = root.findChild(name);
    return child != null && findInFile(child, filePath);
  }

}
