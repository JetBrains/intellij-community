// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public final class JavaExcludedFileIconPatcher implements FileIconPatcher {
  @Override
  public Icon patchIcon(Icon baseIcon, VirtualFile file, int flags, @Nullable Project project) {
    if (project == null) {
      return baseIcon;
    }
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (fileIndex.isInSource(file) && CompilerManager.getInstance(project).isExcludedFromCompilation(file)) {
      return IconManager.getInstance().createLayered(new LayeredIcon(baseIcon, PlatformIcons.EXCLUDED_FROM_COMPILE_ICON));
    }
    return baseIcon;
  }
}
