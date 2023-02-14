// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.editor.CustomFileDropHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;

public final class PluginDropHandler extends CustomFileDropHandler {
  @Override
  public boolean canHandle(@NotNull Transferable transferable,
                           @Nullable Editor editor) {
    List<? extends File> files = FileCopyPasteUtil.getFileList(transferable);
    return !ContainerUtil.isEmpty(files) &&
           ContainerUtil.all(files, file -> {
             String path = file.toPath().toString();
             return FileUtilRt.extensionEquals(path, "jar") ||
                    FileUtilRt.extensionEquals(path, "zip");
           });
  }

  @Override
  public boolean handleDrop(@NotNull Transferable transferable,
                            @Nullable Editor editor,
                            @Nullable Project project) {
    JComponent parent = editor != null ? editor.getComponent() : null;
    List<? extends File> files = FileCopyPasteUtil.getFileList(transferable);
    return !ContainerUtil.isEmpty(files) &&
           ContainerUtil.process(files, file -> installFromDisk(file, project, parent));
  }

  @RequiresEdt
  private static boolean installFromDisk(@NotNull File file,
                                         @Nullable Project project,
                                         @Nullable JComponent parentComponent) {
    return PluginInstaller.installFromDisk(new InstalledPluginsTableModel(project),
                                           PluginEnabler.HEADLESS,
                                           file,
                                           project,
                                           parentComponent,
                                           callbackData -> {
                                             PluginInstaller.installPluginFromCallbackData(callbackData, project, parentComponent);
                                           });
  }
}
