// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.snippets

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls


/**
 * Ways to deal with paths for file names before creating a snippet.
 *
 * When creating a snippet from $PROJECT_ROOT/sub/readme.md and $PROJECT_ROOT/sub/a/Main.java, one can choose
 * to have all names normalized to the nearest common parent directory ($PROJECT_ROOT/sub), or the project
 * root, or just leave out directories all together.
 *
 * Example: Using RelativePaths as the setting should result in two files with names readme.md and a/Main.java
 * being a part of the snippet.
 */
enum class PathHandlingMode(@Nls val displayName: String, @Nls val tooltip: String? = null) {
  /** Uses file paths relative to the nearest common parent directory. */
  RelativePaths(CollaborationToolsBundle.message("snippet.create.path-mode.relative"),
                CollaborationToolsBundle.message("snippet.create.path-mode.relative.tooltip")),

  /** Does not use file paths at all, only file names are used. */
  FlattenedPaths(CollaborationToolsBundle.message("snippet.create.path-mode.none"), CollaborationToolsBundle.message("snippet.create.path-mode.none.tooltip")),

  /** Uses file paths relative to the project root. */
  ContentRootRelativePaths(CollaborationToolsBundle.message("snippet.create.path-mode.content-root-relative"),
                           CollaborationToolsBundle.message("snippet.create.path-mode.content-root-relative.tooltip")),

  /** Uses file paths relative to the project root. */
  ProjectRelativePaths(CollaborationToolsBundle.message("snippet.create.path-mode.project-relative"),
                       CollaborationToolsBundle.message("snippet.create.path-mode.project-relative.tooltip"));

  companion object {
    /**
     * Gets the name of a snippet entry directly from the file name.
     */
    private val pathFromName: suspend (VirtualFile) -> String = { file -> file.name }

    /**
     * Gets the file name extractor function for the given [PathHandlingMode] using the given set of [files][VirtualFile].
     * If the list of files are empty, the name selector used should not matter (and no dialog should be opened anyway),
     * the default name selector is then returned, which is to just take the file name as snippet file name.
     */
    fun getFileNameExtractor(project: Project,
                             files: List<VirtualFile>,
                             pathHandlingMode: PathHandlingMode): suspend (VirtualFile) -> String =
      if (files.isEmpty()) {
        pathFromName
      }
      else {
        when (pathHandlingMode) {
          RelativePaths -> pathFromNearestCommonAncestor(files)
          ProjectRelativePaths -> pathFromProjectRoot(project)
          ContentRootRelativePaths -> pathFromContentRoot(project)
          FlattenedPaths -> pathFromName
        }
      }

    /**
     * Gets the name of a snippet entry from the relative path from the content root of a file.
     */
    private fun pathFromContentRoot(project: Project): suspend (VirtualFile) -> String {
      val pfi = ProjectFileIndex.getInstance(project)
      return { file ->
        readAction { pfi.getContentRootForFile(file) }
          ?.let { root -> VfsUtilCore.getRelativePath(file, root) } ?: file.name
      }
    }

    /**
     * Gets the name of a snippet entry from the relative path from the project root.
     */
    private fun pathFromProjectRoot(project: Project): suspend (VirtualFile) -> String {
      val projectRoot = project.guessProjectDir() ?: return { file -> file.name }
      return { file ->
        VfsUtilCore.getRelativePath(file, projectRoot) ?: file.name
      }
    }

    /**
     * Gets the name of a snippet entry from the nearest common ancestor of all files (could be the file system root directory).
     *
     * @param files The collection of files from which snippet content is gathered. May never be empty.
     */
    private fun pathFromNearestCommonAncestor(files: Collection<VirtualFile>): suspend (VirtualFile) -> String {
      var closestRoot = files.first().parent
      for (file in files.drop(1)) {
        closestRoot = VfsUtilCore.getCommonAncestor(closestRoot, file)
      }
      return { file ->
        VfsUtilCore.getRelativePath(file, closestRoot) ?: file.name
      }
    }
  }
}