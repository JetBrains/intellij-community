// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.scope.packageSet

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NonNls
import java.util.regex.Pattern

internal class ProjectPathPatternPackageSet(@NonNls private val pathPattern: String) : PackageSetBase() {
  private val filePattern: Pattern = Pattern.compile(FilePatternPackageSet.convertToRegexp(StringUtil.trimStart(pathPattern, "/"), '/'))

  override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
    val virtualFile = (file as? VirtualFileWindow)?.delegate ?: file
    val baseDir = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(virtualFile) ?: return false
    val relativePath = VfsUtilCore.getRelativePath(virtualFile, baseDir, '/') ?: return false
    val pathToMatch = if (virtualFile.isDirectory) "$relativePath/" else relativePath
    return filePattern.matcher(pathToMatch).matches()
  }

  override fun createCopy(): PackageSet = ProjectPathPatternPackageSet(pathPattern)

  override fun getNodePriority(): Int = 0

  override fun getText(): String = "$SCOPE_PROJECT_PATH:$pathPattern"

  companion object {
    @NonNls
    const val SCOPE_PROJECT_PATH: String = "projectPath"
  }
}
