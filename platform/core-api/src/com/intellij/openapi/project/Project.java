// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.*;

/**
 * An object representing an IntelliJ project.
 *
 * <p>To get all of its modules, use {@code ModuleManager.getInstance(project).getModules()}.
 *
 * <p>To iterate over all project source files and directories,
 * use {@code ProjectFileIndex.getInstance(project).iterateContent(iterator)}.
 *
 * <p>To get the list of all open projects, use {@code ProjectManager.getInstance().getOpenProjects()}.
 */
@ApiStatus.NonExtendable
public interface Project extends ComponentManager, AreaInstance {
  String DIRECTORY_STORE_FOLDER = ".idea";

  /**
   * Returns a name ot the project. For a directory-based project it's an arbitrary string specified by user at project creation
   * or later in a project settings. For a file-based project it's a name of a project file without extension.
   *
   * @return project name
   */
  @NotNull
  @NlsSafe String getName();

  /**
   * Returns a directory under which project configuration files are stored ({@code .ipr} file or {@code .idea} directory). Note that it
   * is not always the direct parent of {@code .idea} directory, it may be its grand-grand parent.<br/>
   * Returns {@code null} for default project.
   *
   * @deprecated use other methods depending on what you actually need:
   * <ul>
   *   <li>if you need to find a root directory for a file use {@link com.intellij.openapi.roots.ProjectFileIndex#getContentRootForFile getContentRootForFile};</li>
   *   <li>if you have a {@link com.intellij.openapi.module.Module Module} instance in the context, use one of its {@link com.intellij.openapi.roots.ModuleRootModel#getContentRoots() content roots};</li>
   *   <li>if you just need to get a directory somewhere near project files, use {@link com.intellij.openapi.project.ProjectUtil#guessProjectDir guessProjectDir};</li>
   *   <li>if you really need to locate {@code .idea} directory or {@code .ipr} file, use {@link com.intellij.openapi.components.impl.stores.IProjectStore IProjectStore}.</li>
   * </ul>
   */
  @Deprecated
  VirtualFile getBaseDir();

  /**
   * Returns path to a directory under which project configuration files are stored ({@code .ipr} file or {@code .idea} directory). Note that it
   * is not always the direct parent of {@code .idea} directory, it may be its grand-grand parent.<br/>
   * Returns {@code null} for default project.
   * <p>It's <b>strongly recommended</b> to use other methods instead of this one (see {@link #getBaseDir()} for alternatives. Most
   * probably any use of this method in production code may lead to unexpected results for some projects (e.g. if {@code .idea} directory is
   * stored not near the project files).
   * </p>
   */
  @Nullable
  @SystemIndependent @NonNls
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
  @SystemIndependent @NonNls
  String getProjectFilePath();

  /**
   * Returns presentable project path:
   * {@linkplain #getProjectFilePath()} for file-based projects, {@linkplain #getBasePath()} for directory-based ones.<br/>
   * Returns {@code null} for default project.
   * <b>Note:</b> the word "presentable" here implies file system presentation, not a UI one.
   */
  default @Nullable @SystemDependent @NonNls String getPresentableUrl() {
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

  /**
   * Provides access to the project-level {@link MessageBus} instance to send or receive events.
   */
  @Override
  @NotNull MessageBus getMessageBus();

  @NotNull @NonNls
  String getLocationHash();

  void save();

  default void scheduleSave() {
    save();
  }

  boolean isOpen();

  boolean isInitialized();

  default boolean isDefault() {
    return false;
  }
}