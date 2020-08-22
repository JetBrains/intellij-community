// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor;

import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.FileColorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class VcsEditorTabColorProvider implements EditorTabColorProvider, DumbAware {

  @Nullable
  @Override
  public Color getEditorTabColor(@NotNull Project project, @NotNull VirtualFile file) {
    if (file instanceof DiffVirtualFile) {
      FileColorManager fileColorManager = FileColorManager.getInstance(project);
      if (file.getName().equals("Shelf")) {
        return fileColorManager.getColor("Violet");
      }

      return fileColorManager.getColor("Green");
    }

    return null;
  }
}
