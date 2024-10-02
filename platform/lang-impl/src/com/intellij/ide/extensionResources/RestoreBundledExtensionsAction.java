// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.extensionResources;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
public final class RestoreBundledExtensionsAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    boolean isDirectory = file != null && file.isDirectory();
    e.getPresentation().setEnabledAndVisible(isDirectory && ExtensionsRootType.getInstance().getPath(file) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ExtensionsRootType extensionsRootType = ExtensionsRootType.getInstance();

    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    PluginId pluginId = extensionsRootType.getOwner(file);
    String path = extensionsRootType.getPath(file);

    assert file != null && pluginId != null && path != null;

    Task.Backgroundable extractResourcesInBackground = new Task.Backgroundable(
      e.getProject(),
      LangBundle.message("progress.title.extracting.bundled.extensions.for.plugin", pluginId.getIdString())) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          extensionsRootType.extractBundledResources(pluginId, path);
          VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
        }
        catch (IOException ex) {
          ExtensionsRootType.LOG.warn("Failed to extract bundled extensions for " + file.getPath(), ex);
        }
      }
    };
    ProgressManager.getInstance().run(extractResourcesInBackground);
  }
}
