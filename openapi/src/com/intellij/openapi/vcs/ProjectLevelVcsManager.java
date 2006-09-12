/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Manages the version control systems used by a specific project.
 */
public abstract class ProjectLevelVcsManager {
  @NonNls public static final String FILE_VIEW_TOOL_WINDOW_ID = "File View";

  /**
   * Returns the <code>ProjectLevelVcsManager<code> instance for the specified project.
   *
   * @param project the project for which the instance is requested.
   * @return the manager instance.
   */
  public static ProjectLevelVcsManager getInstance(Project project) {
    return project.getComponent(ProjectLevelVcsManager.class);
  }

  /**
   * Returns the list of all registered version control systems.
   *
   * @return the list of registered version control systems.
   */
  public abstract AbstractVcs[] getAllVcss();

  /**
   * Returns the version control system with the specified name.
   *
   * @param name the name of the VCS to find.
   * @return the VCS instance, or null if none is found.
   */
  public abstract AbstractVcs findVcsByName(String name);

  /**
   * Checks if all files in the specified array are managed by the specified VCS.
   *
   * @param abstractVcs the VCS to check.
   * @param files       the files to check.
   * @return true if all files are managed by the VCS, false otherwise.
   */
  public abstract boolean checkAllFilesAreUnder(AbstractVcs abstractVcs, VirtualFile[] files);

  /**
   * Returns the VCS managing the specified file.
   *
   * @param file the file to check.
   * @return the VCS instance, or null if the file does not belong to any module or the module
   *         it belongs to is not under version control.
   */
  public abstract AbstractVcs getVcsFor(VirtualFile file);

  /**
   * Checks if the specified VCS is used by any of the modules in the project.
   *
   * @param vcs the VCS to check.
   * @return true if the VCS is used by any of the modules, false otherwise
   */
  public abstract boolean checkVcsIsActive(AbstractVcs vcs);

  /**
   * Returns the user-visible relative path from the content root under which the
   * specified file is located to the file itself, prefixed by the module name in
   * angle brackets.
   *
   * @param file the file for which the path is requested.
   * @return the relative path.
   */
  public abstract String getPresentableRelativePathFor(VirtualFile file);

  public abstract DataProvider createVirtualAndPsiFileDataProvider(VirtualFile[] virtualFileArray, VirtualFile selectedFile);

  /**
   * Returns the list of all modules in the project which are managed by the specified VCS.
   *
   * @param vcs the CVS to check.
   * @return the list of modules under the VCS.
   */
  public abstract Module[] getAllModulesUnder(AbstractVcs vcs);

  /**
   * Returns the list of VCSes used by at least one module in the project.
   *
   * @return the list of VCSes used in the project.
   */
  public abstract AbstractVcs[] getAllActiveVcss();

  public abstract void addMessageToConsoleWindow(String message, TextAttributes attributes);

  @NotNull
  public abstract VcsShowSettingOption getStandardOption(@NotNull VcsConfiguration.StandardOption option,
                                                         @NotNull AbstractVcs vcs);

  @NotNull
  public abstract VcsShowConfirmationOption getStandardConfirmation(@NotNull VcsConfiguration.StandardConfirmation option,
                                                                    @NotNull AbstractVcs vcs);

  @NotNull
  public abstract VcsShowSettingOption getOrCreateCustomOption(@NotNull String vcsActionName,
                                                               @NotNull AbstractVcs vcs);


  /**
   * Returns the list of all registered factories which provide callbacks to run before and after
   * VCS checkin operations.
   *
   * @return the list of registered factories.
   * @since 5.1
   */
  public abstract List<CheckinHandlerFactory> getRegisteredCheckinHandlerFactories();

  /**
   * Registers a factory which provides callbacks to run before and after VCS checkin operations.
   *
   * @param factory the factory to register.
   * @since 5.1
   */
  public abstract void registerCheckinHandlerFactory(CheckinHandlerFactory factory);

  /**
   * Unregisters a factory which provides callbacks to run before and after VCS checkin operations.
   *
   * @param factory the factory to unregister.
   * @since 5.1
   */
  public abstract void unregisterCheckinHandlerFactory(CheckinHandlerFactory factory);

  /**
   * Adds a listener for receiving notifications about changes in VCS configuration for the project.
   *
   * @param listener the listener instance.
   * @since 6.0
   */
  public abstract void addVcsListener(VcsListener listener);

  /**
   * Removes a listener for receiving notifications about changes in VCS configuration for the project.
   *
   * @param listener the listener instance.
   * @since 6.0
   */
  public abstract void removeVcsListener(VcsListener listener);

  /**
   * Marks the beginning of a background VCS operation (commit or update).
   *
   * @since 6.0
   */
  public abstract void startBackgroundVcsOperation();

  /**
   * Marks the end of a background VCS operation (commit or update).
   *
   * @since 6.0
   */
  public abstract void stopBackgroundVcsOperation();

  /**
   * Checks if a background VCS operation (commit or update) is currently in progress.
   *
   * @return true if a background operation is in progress, false otherwise.
   * @since 6.0
   */
  public abstract boolean isBackgroundVcsOperationRunning();
}
