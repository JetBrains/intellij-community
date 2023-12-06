// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.components.State;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * @deprecated use {@link OverrideFileTypeManager} instead
 */
@Deprecated(forRemoval = true)
@State(name = "ProjectPlainTextFileTypeManager")
public final class ProjectPlainTextFileTypeManager extends PersistentFileSetManager {
  public static ProjectPlainTextFileTypeManager getInstance(@NotNull Project project) {
    return project.getService(ProjectPlainTextFileTypeManager.class);
  }

  /**
  * @deprecated use {@link OverrideFileTypeManager#getFiles()} instead
  */
  @Deprecated
  @Override
  public @NotNull Collection<VirtualFile> getFiles() {
    return OverrideFileTypeManager.getInstance().getFiles();
  }

  @Override
  public void loadState(@NotNull Element state) {
    super.loadState(state);

    LinkedHashMap<VirtualFile, FileType> files = new LinkedHashMap<>();
    for (VirtualFile file : super.getFiles()) {
      if (OverrideFileTypeManager.isOverridable(file.getFileType())) {
        files.put(file, PlainTextFileType.INSTANCE);
      }
    }

    OverrideFileTypeManager.getInstance().addFiles(files);
  }
}
