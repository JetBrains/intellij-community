/**
 * @author cdr
 */
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class LibraryUtil {
  private LibraryUtil() {
  }

  public static boolean isClassAvailableInLibrary(final Library library, final String fqn) {
    return isClassAvailableInLibrary(library.getFiles(OrderRootType.CLASSES), fqn);
  }

  public static boolean isClassAvailableInLibrary(VirtualFile[] files, final String fqn) {
    for (VirtualFile file : files) {
      if (findInFile(file, new StringTokenizer(fqn, "."))) return true;
    }
    return false;
  }

  @Nullable
  public static Library findLibraryByClass(final String fqn, Project project) {
    if (project != null) {
      final LibraryTable projectTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
      Library library = findInTable(projectTable, fqn);
      if (library != null) {
        return library;
      }
    }
    final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable();
    return findInTable(table, fqn);
  }


  private static boolean findInFile(VirtualFile file, final StringTokenizer tokenizer) {
    if (!tokenizer.hasMoreTokens()) return true;
    @NonNls StringBuffer name = new StringBuffer(tokenizer.nextToken());
    if (!tokenizer.hasMoreTokens()) {
      name.append(".class");
    }
    final VirtualFile child = file.findChild(name.toString());
    return child != null && findInFile(child, tokenizer);
  }

  private static Library findInTable(LibraryTable table, String fqn) {
    for (Library library : table.getLibraries()) {
      if (isClassAvailableInLibrary(library, fqn)) {
        return library;
      }
    }
    return null;
  }

}