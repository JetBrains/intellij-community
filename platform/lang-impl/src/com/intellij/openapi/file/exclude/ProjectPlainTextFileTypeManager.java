// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.components.State;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Rustam Vishnyakov
 * @deprecated use {@link OverrideFileTypeManager} instead
 */
@Deprecated(forRemoval = true)
@State(name = "ProjectPlainTextFileTypeManager")
public class ProjectPlainTextFileTypeManager extends PersistentFileSetManager {
  public static ProjectPlainTextFileTypeManager getInstance(@NotNull Project project) {
    return project.getService(ProjectPlainTextFileTypeManager.class);
  }

  /**
  * @deprecated use {@link OverrideFileTypeManager#getFiles()} instead
  */
  @Deprecated
  @Override
  @NotNull
  public Collection<VirtualFile> getFiles() {
    return OverrideFileTypeManager.getInstance().getFiles();
  }

  @Override
  public void loadState(@NotNull Element state) {
    super.loadState(state);
    for (VirtualFile file : super.getFiles()) {
      if (!OverrideFileTypeManager.isOverridable(file.getFileType())) {
        continue;
      }
      OverrideFileTypeManager.getInstance().addFile(file, PlainTextFileType.INSTANCE);
    }
  }
}
