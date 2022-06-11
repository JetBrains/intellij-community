// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Retrieves plain text file type from open projects' configurations.
 *
 * @author Rustam Vishnyakov
 * @deprecated use {@link OverrideFileTypeManager} instead
 */
@Deprecated(forRemoval = true)
@Service
public final class EnforcedPlainTextFileTypeManager {
  public static EnforcedPlainTextFileTypeManager getInstance() {
    return ApplicationManager.getApplication().getService(EnforcedPlainTextFileTypeManager.class);
  }

  /**
   * @deprecated use {@link OverrideFileTypeManager#removeFile(VirtualFile)} instead
   */
  @Deprecated(forRemoval = true)
  public void resetOriginalFileType(@NotNull Project project, @NotNull VirtualFile @NotNull ... files) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
      OverrideFileTypeManager overrideFileTypeManager = OverrideFileTypeManager.getInstance();
      for (VirtualFile file : files) {
        if (fileIndex.isInContent(file) || fileIndex.isInLibrarySource(file) || fileIndex.isExcluded(file)) {
          overrideFileTypeManager.removeFile(file);
        }
      }
    });
  }
}
