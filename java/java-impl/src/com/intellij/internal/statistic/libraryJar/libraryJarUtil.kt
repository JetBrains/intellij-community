// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryJar

import com.intellij.openapi.util.io.JarUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.ProjectScope
import java.util.jar.Attributes

private const val DIGIT_VERSION_PATTERN_PART = "(\\d+.\\d+|\\d+)"
private val JAR_FILE_NAME_PATTERN = Regex("[\\w|\\-.]+-($DIGIT_VERSION_PATTERN_PART[\\w|.]*)jar")

private fun versionByJarManifest(file: VirtualFile): String? = JarUtil.getJarAttribute(
  VfsUtilCore.virtualToIoFile(file),
  Attributes.Name.IMPLEMENTATION_VERSION,
)

private fun versionByJarFileName(fileName: String): String? {
  val fileNameMatcher = JAR_FILE_NAME_PATTERN.matchEntire(fileName) ?: return null
  val value = fileNameMatcher.groups[1]?.value ?: return null
  return value.trimEnd('.')
}

private fun PsiElement.findCorrespondingVirtualFile(): VirtualFile? =
  if (this is PsiPackage)
    getDirectories(ProjectScope.getLibrariesScope(project)).firstOrNull()?.virtualFile
  else
    containingFile?.virtualFile

internal fun findJarVersion(elementFromJar: PsiElement): String? {
  val vFile = elementFromJar.findCorrespondingVirtualFile() ?: return null
  val jarFile = JarFileSystem.getInstance().getVirtualFileForJar(vFile) ?: return null
  val version = versionByJarManifest(jarFile) ?: versionByJarFileName(jarFile.name) ?: return null
  return version.takeIf(String::isNotEmpty)
}
