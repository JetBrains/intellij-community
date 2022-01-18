// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl.todo;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileTypeExtension;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.IndexedFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class TodoIndexers extends FileTypeExtension<DataIndexer<TodoIndexEntry, Integer, FileContent>> {
  public static final TodoIndexers INSTANCE = new TodoIndexers();

  private static final ExtensionPointName<ExtraPlaceChecker> EP_NAME = ExtensionPointName.create("com.intellij.todoExtraPlaces");

  private TodoIndexers() {
    super("com.intellij.todoIndexer");
  }

  public static boolean needsTodoIndex(@NotNull IndexedFile file) {
    if (FileBasedIndex.IGNORE_PLAIN_TEXT_FILES && file.getFileType() == PlainTextFileType.INSTANCE) {
      return false;
    }
    VirtualFile vFile = file.getFile();

    if (!vFile.isInLocalFileSystem()) {
      return false;
    }

    Project project = file.getProject();
    if (project != null && ProjectFileIndex.getInstance(project).isInContent(vFile)) {
      return true;
    }

    for (ExtraPlaceChecker checker : EP_NAME.getExtensionList()) {
      if (checker.accept(project, file.getFile())) {
        return true;
      }
    }
    return false;
  }

  public static boolean belongsToProject(@NotNull Project project, @NotNull VirtualFile file) {
    for (ExtraPlaceChecker checker : EP_NAME.getExtensionList()) {
      if (checker.accept(project, file)) {
        return true;
      }
    }
    if (!ProjectFileIndex.getInstance(project).isInContent(file)) {
      return false;
    }
    return true;
  }

  /**
   * Provides a way to expand TO-DO index filter beyond open projects content scope.
   * It shall be used primarily to create the index for files inside custom {@link com.intellij.util.indexing.IndexableSetContributor} implementations.
   */
  public interface ExtraPlaceChecker {
    boolean accept(@Nullable Project project, @NotNull VirtualFile file);
  }
}
