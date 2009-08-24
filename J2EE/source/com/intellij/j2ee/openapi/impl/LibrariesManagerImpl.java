package com.intellij.j2ee.openapi.impl;

import com.intellij.javaee.LibrariesManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * author: lesya
 */
public class LibrariesManagerImpl extends LibrariesManager {

  @Override
  public VirtualFile findJarByClass(Library library, @NonNls String fqn) {
    return library == null ? null : findJarByClass(library.getFiles(OrderRootType.CLASSES), fqn);
  }

  private VirtualFile findJarByClass(VirtualFile[] files, String fqn) {
    for (VirtualFile file : files) {
      if (findInFile(file, new StringTokenizer(fqn, "."))) return file;
    }
    return null;
  }

  public boolean isClassAvailableInLibrary(Library library, String fqn) {
    final String[] urls = library.getUrls(OrderRootType.CLASSES);
    return isClassAvailable(urls, fqn);
  }

  public boolean isClassAvailable(final String[] urls, String fqn) {
    for (String url : urls) {
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      if (file == null) continue;
      if (!(file.getFileSystem() instanceof JarFileSystem) && !file.isDirectory()) {
        file = JarFileSystem.getInstance().findFileByPath(file.getPath() + JarFileSystem.JAR_SEPARATOR);
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
