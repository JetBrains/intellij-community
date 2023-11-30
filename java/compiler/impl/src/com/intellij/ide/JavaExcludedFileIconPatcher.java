// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IconManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

final class JavaExcludedFileIconPatcher implements FileIconPatcher {
  @Override
  public Icon patchIcon(Icon baseIcon, VirtualFile file, int flags, @Nullable Project project) {
    if (project == null) {
      return baseIcon;
    }
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (fileIndex.isInSource(file) && CompilerManager.getInstance(project).isExcludedFromCompilation(file)) {
      return IconManager.getInstance().createLayered(LayeredIcon.layeredIcon(new Icon[]{baseIcon, PlatformIcons.EXCLUDED_FROM_COMPILE_ICON}));
    }
    return baseIcon;
  }
}
