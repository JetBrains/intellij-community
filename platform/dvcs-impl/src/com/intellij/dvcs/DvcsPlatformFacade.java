/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * IntelliJ code provides a lot of statical bindings to the interested pieces of data. For example we need to execute code
 * like below to get list of modules for the target project:
 * <pre>
 *   ModuleManager.getInstance(project).getModules()
 * </pre>
 * That means that it's not possible to test target classes in isolation if corresponding infrastructure is not set up.
 * However, we don't want to set it up if we execute a simple standalone test.
 * <p/>
 * This interface is intended to encapsulate access to the underlying IntelliJ functionality.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Kirill Likhodedov
 */
public interface DvcsPlatformFacade {

  @NotNull
  AbstractVcs getVcs(@NotNull Project project);

  @NotNull
  ProjectLevelVcsManager getVcsManager(@NotNull Project project);

  void showDialog(@NotNull DialogWrapper dialog);

  @NotNull
  ProjectRootManager getProjectRootManager(@NotNull Project project);

  /**
   * Invokes {@link com.intellij.openapi.application.Application#runReadAction(Computable)}.
   */
  <T> T runReadAction(@NotNull Computable<T> computable);

  void runReadAction(@NotNull Runnable runnable);

  void runWriteAction(@NotNull Runnable runnable);

  void invokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState modalityState);

  void executeOnPooledThread(@NotNull Runnable runnable);

  ChangeListManagerEx getChangeListManager(@NotNull Project project);

  LocalFileSystem getLocalFileSystem();

  @NotNull
  AbstractVcsHelper getVcsHelper(@NotNull Project project);

  @Nullable
  IdeaPluginDescriptor getPluginByClassName(@NotNull String name);

  /**
   * Gets line separator of the given virtual file.
   * If {@code detect} is set {@code true}, and the information about line separator wasn't retrieved yet, loads the file and detects.
   */
  @Nullable
  String getLineSeparator(@NotNull VirtualFile file, boolean detect);

  void saveAllDocuments();

  @NotNull
  ProjectManagerEx getProjectManager();

  @NotNull
  SaveAndSyncHandler getSaveAndSyncHandler();

  void hardRefresh(@NotNull VirtualFile root);
}
