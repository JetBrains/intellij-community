// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.annotations.SystemIndependent;

/**
 * An object representing an IntelliJ project.
 *
 * <p>To get all of its modules, use {@code ModuleManager.getInstance(project).getModules()}.
 *
 * <p>To iterate over all project source files and directories,
 * use {@code ProjectFileIndex.SERVICE.getInstance(project).iterateContent(iterator)}.
 *
 * <p>To get the list of all open projects, use {@code ProjectManager.getInstance().getOpenProjects()}.
 */
public interface Project extends ComponentManager, AreaInstance {
  String DIRECTORY_STORE_FOLDER = ".idea";

  /**
   * Returns a name ot the project. For a directory-based project it's an arbitrary string specified by user at project creation
   * or later in a project settings. For a file-based project it's a name of a project file without extension.
   *
   * @return project name
   */
  @NotNull
  String getName();

  /**
   * Returns a project base directory - a parent directory of a {@code .ipr} file or {@code .idea} directory.<br/>
   * Returns {@code null} for default project.
   *
   * @see com.intellij.openapi.project.ProjectUtil#guessProjectDir
   * @see #getBasePath()
   *
   * @deprecated No such concept as "project root". Project consists of module set, each has own content root set.
   */
  @Deprecated
  VirtualFile getBaseDir();

  /**
   * Returns a path to a project base directory (see {@linkplain #getBaseDir()}).<br/>
   * Returns {@code null} for default project.
   *
   * @see com.intellij.openapi.project.ProjectUtil#guessProjectDir
   */
  @Nullable
  @SystemIndependent
  String getBasePath();

  /**
   * Returns project descriptor file:
   * <ul>
   *   <li>{@code path/to/project/project.ipr} - for file-based projects</li>
   *   <li>{@code path/to/project/.idea/misc.xml} - for directory-based projects</li>
   * </ul>
   * Returns {@code null} for default project.
   *
   * @return project descriptor file, or null for default project
   */
  @Nullable
  VirtualFile getProjectFile();

  /**
   * @return a path to project file (see {@linkplain #getProjectFile()}) or {@code null} for default project.
   */
  @Nullable
  @SystemIndependent
  String getProjectFilePath();

  /**
   * Returns presentable project path:
   * {@linkplain #getProjectFilePath()} for file-based projects, {@linkplain #getBasePath()} for directory-based ones.<br/>
   * Returns {@code null} for default project.
   * <b>Note:</b> the word "presentable" here implies file system presentation, not a UI one.
   */
  @Nullable
  @SystemDependent
  default String getPresentableUrl() {
    return null;
  }

  /**
   * <p>Returns a workspace file:
   * <ul>
   *   <li>{@code path/to/project/project.iws} - for file-based projects</li>
   *   <li>{@code path/to/project/.idea/workspace.xml} - for directory-based ones</li>
   * </ul>
   * Returns {@code null} for default project.
   *
   * @return workspace file, or null for default project
   */
  @Nullable
  VirtualFile getWorkspaceFile();

  @NotNull
  String getLocationHash();

  void save();

  boolean isOpen();

  boolean isInitialized();

  default boolean isDefault() {
    return false;
  }
}