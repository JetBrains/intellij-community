package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.impl.ui.LibraryElementPresentation;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class PackagingEditorUtil {
  private PackagingEditorUtil() {
  }

  public static String getLibraryItemText(final @NotNull Library library, final boolean includeTableName) {
    String name = library.getName();
    VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
    if (name != null) {
      return name + (includeTableName ? LibraryElementPresentation.getLibraryTableComment(library) : "");
    }
    else if (files.length > 0) {
      return files[0].getName() + (includeTableName ? LibraryElementPresentation.getLibraryTableComment(library) : "");
    }
    else {
      return ProjectBundle.message("library.empty.item");
    }
  }

}
