// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.editor.CustomFileDropHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;
import java.io.File;

public final class PluginDropHandler extends CustomFileDropHandler {
  @Override
  public boolean canHandle(@NotNull Transferable transferable, @Nullable Editor editor) {
    File file = getFile(transferable);
    if (file == null) {
      return false;
    }

    String path = file.toPath().toString();
    return FileUtilRt.extensionEquals(path, "jar") ||
           FileUtilRt.extensionEquals(path, "zip");
  }

  @Override
  public boolean handleDrop(@NotNull Transferable transferable,
                            @Nullable Editor editor,
                            Project project) {
    File file = getFile(transferable);
    return file != null &&
           PluginInstaller.installFromDisk(project, file);
  }

  private static @Nullable File getFile(@NotNull Transferable transferable) {
    return ContainerUtil.getOnlyItem(FileCopyPasteUtil.getFileList(transferable));
  }
}
