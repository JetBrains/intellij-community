// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TargetEnvironmentPaths")

package com.intellij.execution.target

import com.intellij.execution.Platform
import com.intellij.openapi.util.io.FileUtil

data class PathMapping(val localPath: String, val targetPath: String)

fun TargetEnvironment.getLocalPaths(targetPath: String): List<String> {
  return findPathVariants(
    mappings = collectPathMappings(),
    sourcePath = targetPath,
    sourcePathFun = { pathMapping -> pathMapping.targetPath },
    sourceFileSeparator = targetPlatform.platform.fileSeparator,
    destPathFun = { pathMapping -> pathMapping.localPath },
    destFileSeparator = Platform.current().fileSeparator
  )
}

fun TargetEnvironment.getTargetPaths(localPath: String): List<String> {
  return findPathVariants(
    mappings = collectPathMappings(),
    sourcePath = localPath,
    sourcePathFun = { pathMapping -> pathMapping.localPath },
    sourceFileSeparator = Platform.current().fileSeparator,
    destPathFun = { pathMapping -> pathMapping.targetPath },
    destFileSeparator = targetPlatform.platform.fileSeparator
  )
}

private fun TargetEnvironment.collectPathMappings(): List<PathMapping> =
  uploadVolumes.values.map { PathMapping(localPath = it.localRoot.toString(), targetPath = it.targetRoot) }

private fun findPathVariants(mappings: Iterable<PathMapping>,
                             sourcePath: String,
                             sourcePathFun: (PathMapping) -> String,
                             sourceFileSeparator: Char,
                             destPathFun: (PathMapping) -> String,
                             destFileSeparator: Char): List<String> {
  return mappings.mapNotNull { mapping ->
    val sourceBase = sourcePathFun(mapping)
    if (FileUtil.isAncestor(sourceBase, sourcePath, false)) {
      val destBase = destPathFun(mapping)
      FileUtil.getRelativePath(sourceBase, sourcePath, sourceFileSeparator)?.let { relativeSourcePath ->
        joinTargetPaths(destBase, relativeSourcePath, destFileSeparator)
      }
    }
    else {
      null
    }
  }
}

internal fun joinTargetPaths(basePath: String, relativePath: String, fileSeparator: Char): String {
  val resultCanonicalPath = FileUtil.toCanonicalPath("$basePath$fileSeparator$relativePath", fileSeparator)
  // The method `FileUtil.toCanonicalPath()` returns the path with '/' no matter what `fileSeparator` is passed but let's make the result
  // system-dependent
  return FileUtil.toSystemDependentName(resultCanonicalPath, fileSeparator)
}