// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.fileChooser.FileChooserPanel;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public final class GotoModuleDirAction extends FileChooserAction {
  @Override
  protected void update(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(getModulePath(e) != null);
  }

  @Override
  protected void actionPerformed(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    Path path = getModulePath(e);
    if (path != null) {
      panel.load(path);
    }
  }

  private static @Nullable Path getModulePath(AnActionEvent e) {
    VirtualFile dir = getModuleDir(e);
    return dir != null ? dir.getFileSystem().getNioPath(dir) : null;
  }

  @Override
  protected void update(@NotNull FileSystemTree fileSystemTree, @NotNull AnActionEvent e) {
    VirtualFile moduleDir = getModuleDir(e);
    e.getPresentation().setEnabled(moduleDir != null && fileSystemTree.isUnderRoots(moduleDir));
  }

  @Override
  protected void actionPerformed(@NotNull FileSystemTree fileSystemTree, @NotNull AnActionEvent e) {
    VirtualFile moduleDir = getModuleDir(e);
    if (moduleDir != null) {
      fileSystemTree.select(moduleDir, () -> fileSystemTree.expand(moduleDir, null));
    }
  }

  private static @Nullable VirtualFile getModuleDir(AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE_CONTEXT);
    if (module == null) {
      module = e.getData(PlatformCoreDataKeys.MODULE);
    }

    if (module != null && !module.isDisposed()) {
      final VirtualFile moduleFile = module.getModuleFile();
      if (moduleFile != null && moduleFile.isValid()) {
        final VirtualFile moduleDir = moduleFile.getParent();
        if (moduleDir != null && moduleDir.isValid()) {
          return moduleDir;
        }
      }
    }

    return null;
  }
}
