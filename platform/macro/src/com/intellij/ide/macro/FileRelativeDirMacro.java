// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class FileRelativeDirMacro extends Macro {
  @NotNull
  @Override
  public String getName() {
    return "FileRelativeDir";
  }

  @NotNull
  @Override
  public String getDescription() {
    return IdeCoreBundle.message("macro.file.directory.relative");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    final VirtualFile baseDir = PlatformCoreDataKeys.PROJECT_FILE_DIRECTORY.getData(dataContext);
    if (baseDir == null) {
      return null;
    }

    VirtualFile dir = getVirtualDirOrParent(dataContext);
    if (dir == null) return null;
    return FileUtil.getRelativePath(VfsUtilCore.virtualToIoFile(baseDir), VfsUtilCore.virtualToIoFile(dir));
  }
}
