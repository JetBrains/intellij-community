// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.logging

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectAndLibrariesScope

/**
 * Scope which contains project files and libraries which are only available in the project, e.g. it excludes libraries for
 * the configuration scripts like Gradle build script.
 * */
public class ProjectContainingLibrariesScope(project: Project) : ProjectAndLibrariesScope(project) {
  private val projectFileIndex: ProjectFileIndex = ProjectFileIndex.getInstance(project);

  override fun contains(file: VirtualFile): Boolean = !projectFileIndex.findContainingLibraries(file).isEmpty()

  public companion object {
    private val PROJECT_CONTAINING_LIBRARIES_SCOPE_KEY = NotNullLazyKey.createLazyKey<GlobalSearchScope, Project>(
      "PROJECT_CONTAINING_LIBRARIES_SCOPE_KEY"
    ) { project: Project -> ProjectContainingLibrariesScope(project) }

    @JvmStatic
    public fun getScope(project: Project): GlobalSearchScope = PROJECT_CONTAINING_LIBRARIES_SCOPE_KEY.getValue(project)
  }
}