// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaSourceFiles")

package com.intellij.java.workspace.files

import com.intellij.java.workspace.entities.asJavaResourceRoot
import com.intellij.java.workspace.entities.asJavaSourceRoot
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.sourceRoots
import com.intellij.workspaceModel.ide.legacyBridge.SourceRootTypeRegistry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.module.JpsModuleSourceRootType

/**
 * Return a file located under the source or resource roots of this module which will be copied to the production output with [relativePath].
 */
fun ModuleEntity.findResourceFileByRelativePath(
  relativePath: String
): VirtualFile? = productionSourceRoots.findResourceFileByRelativePath(relativePath)

/**
 * Return a file located under the roots of this module with one of [rootTypes] which will be copied to the output with [relativePath].
 */
@ApiStatus.Internal
fun ModuleEntity.findResourceFileByRelativePath(
  relativePath: String,
  rootTypes: Set<JpsModuleSourceRootType<*>>
): VirtualFile? = sourceRoots(rootTypes).findResourceFileByRelativePath(relativePath)

/**
 * Return files located under the source or resource roots of this module which will be copied to the production output directly into [relativePath].
 */
@ApiStatus.Internal
fun ModuleEntity.findResourceFilesInDirectory(
  relativePath: String
): Sequence<VirtualFile> = productionSourceRoots.findResourceFilesInDirectory(relativePath)

/**
 * Return files located under the roots of this module with one of [rootTypes] which will be copied to the output directly into [relativePath].
 */
@ApiStatus.Internal
fun ModuleEntity.findResourceFilesInDirectory(
  relativePath: String,
  rootTypes: Set<JpsModuleSourceRootType<*>>
): Sequence<VirtualFile> = sourceRoots(rootTypes).findResourceFilesInDirectory(relativePath)

private fun Sequence<SourceRootEntity>.findResourceFileByRelativePath(path: String): VirtualFile? {
  val trimmedPath = path.trim('/')
  return firstNotNullOfOrNull { sourceRoot ->
    val root = sourceRoot.url.virtualFile ?: return@firstNotNullOfOrNull null
    val prefix = sourceRoot.outputPrefix()
    if (!root.isDirectory) return@firstNotNullOfOrNull root.takeIf { trimmedPath.removePrefixPath(prefix) == root.name }
    val pathInRoot = trimmedPath.removePrefixPath(prefix) ?: return@firstNotNullOfOrNull null
    root.findFileByRelativePath(pathInRoot)?.takeIf { !it.isDirectory }
  }
}

private fun Sequence<SourceRootEntity>.findResourceFilesInDirectory(path: String): Sequence<VirtualFile> {
  val trimmedPath = path.trim('/')
  return flatMap { sourceRoot ->
    val root = sourceRoot.url.virtualFile ?: return@flatMap emptySequence()
    val prefix = sourceRoot.outputPrefix()
    if (!root.isDirectory) return@flatMap if (prefix == trimmedPath) sequenceOf(root) else emptySequence()
    val pathInRoot = trimmedPath.removePrefixPath(prefix) ?: return@flatMap emptySequence()
    val directory = root
      .findFileByRelativePath(pathInRoot)
      ?.takeIf { it.isDirectory }
      ?: return@flatMap emptySequence()
    directory.children.asSequence().filter { !it.isDirectory }
  }.distinct()
}

private fun String.removePrefixPath(prefix: String): String? = when {
  prefix.isEmpty() -> this
  this == prefix -> ""
  startsWith("$prefix/") -> substring(prefix.length + 1)
  else -> null
}

private val ModuleEntity.productionSourceRoots
  get(): Sequence<SourceRootEntity> = sourceRoots
    .asSequence()
    .filterNot { it.rootType?.isForTests == true }

private fun ModuleEntity.sourceRoots(rootTypes: Set<JpsModuleSourceRootType<*>>): Sequence<SourceRootEntity> = sourceRoots
  .asSequence()
  .filter { it.rootType in rootTypes }

private val SourceRootEntity.rootType
  get(): JpsModuleSourceRootType<*>? = SourceRootTypeRegistry.getInstance().findTypeById(rootTypeId)

private fun SourceRootEntity.outputPrefix(): String {
  val prefix = asJavaSourceRoot()?.packagePrefix?.replace('.', '/')
               ?: asJavaResourceRoot()?.relativeOutputPath
  return prefix.orEmpty().trim('/')
}