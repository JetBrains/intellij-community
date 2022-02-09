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
        joinTargetPaths(destBase, relativeSourcePath, fileSeparator = destFileSeparator)
      }
    }
    else {
      null
    }
  }
}

fun joinTargetPaths(vararg paths: String, fileSeparator: Char): String {
  val iterator = paths.iterator()
  var path = iterator.next()
    .normalizeFileSeparatorCharacter(fileSeparator)
    .removeRepetitiveFileSeparators(fileSeparator)
  while (iterator.hasNext()) {
    val basePath = path
      .ensureEndsWithFileSeparator(fileSeparator)
    val normalizedRelativePath = iterator.next()
      .normalizeFileSeparatorCharacter(fileSeparator)
      .removeRepetitiveFileSeparators(fileSeparator)
      .normalizeRelativePath(fileSeparator)
    path = "$basePath$normalizedRelativePath"
  }
  return path
}

private fun String.normalizeFileSeparatorCharacter(fileSeparator: Char): String =
  if (fileSeparator == '\\') replace('/', fileSeparator) else this

private fun String.removeRepetitiveFileSeparators(fileSeparator: Char): String =
  replace("$fileSeparator$fileSeparator", fileSeparator.toString())

private fun String.normalizeRelativePath(fileSeparator: Char): String =
  when {
    length == 1 && this[0] == '.' -> ""
    startsWith(prefix = ".$fileSeparator") -> substring(startIndex = 2)
    else -> this
  }.removeSuffix(fileSeparator.toString())

private fun String.ensureEndsWithFileSeparator(fileSeparator: Char): String = if (endsWith(fileSeparator)) this else "$this$fileSeparator"