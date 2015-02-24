/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.dvcs;

import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
public abstract class DvcsPlatformFacadeImpl implements DvcsPlatformFacade {

  @NotNull
  @Override
  public ProjectLevelVcsManager getVcsManager(@NotNull Project project) {
    return ProjectLevelVcsManager.getInstance(project);
  }

  @Override
  public void showDialog(@NotNull DialogWrapper dialog) {
    dialog.show();
  }

  @NotNull
  @Override
  public ProjectRootManager getProjectRootManager(@NotNull Project project) {
    return ProjectRootManager.getInstance(project);
  }

  @Override
  public <T> T runReadAction(@NotNull Computable<T> computable) {
    return ApplicationManager.getApplication().runReadAction(computable);
  }

  @Override
  public void runReadAction(@NotNull Runnable runnable) {
    ApplicationManager.getApplication().runReadAction(runnable);
  }

  @Override
  public void runWriteAction(@NotNull Runnable runnable) {
    ApplicationManager.getApplication().runWriteAction(runnable);
  }

  @Override
  public void invokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
    ApplicationManager.getApplication().invokeAndWait(runnable, modalityState);
  }

  @Override
  public void executeOnPooledThread(@NotNull Runnable runnable) {
    ApplicationManager.getApplication().executeOnPooledThread(runnable);
  }

  @Override
  public ChangeListManagerEx getChangeListManager(@NotNull Project project) {
    return (ChangeListManagerEx)ChangeListManager.getInstance(project);
  }

  @Override
  public LocalFileSystem getLocalFileSystem() {
    return LocalFileSystem.getInstance();
  }

  @NotNull
  @Override
  public AbstractVcsHelper getVcsHelper(@NotNull Project project) {
    return AbstractVcsHelper.getInstance(project);
  }

  @Nullable
  @Override
  public IdeaPluginDescriptor getPluginByClassName(@NotNull String name) {
    return PluginManager.getPlugin(PluginManager.getPluginByClassName(name));
  }

  @Nullable
  @Override
  public String getLineSeparator(@NotNull VirtualFile file, boolean detect) {
    return LoadTextUtil.detectLineSeparator(file, detect);
  }

  @Override
  public void saveAllDocuments() {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }

  @NotNull
  @Override
  public ProjectManagerEx getProjectManager() {
    return ProjectManagerEx.getInstanceEx();
  }

  @NotNull
  @Override
  public SaveAndSyncHandler getSaveAndSyncHandler() {
    return SaveAndSyncHandler.getInstance();
  }

  @Override
  public void hardRefresh(@NotNull VirtualFile root) {
    VfsUtil.markDirtyAndRefresh(true, true, false, root);
  }

}
