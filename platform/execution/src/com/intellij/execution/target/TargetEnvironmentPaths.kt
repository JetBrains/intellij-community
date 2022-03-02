// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TargetEnvironmentPaths")

package com.intellij.execution.target

import com.intellij.execution.Platform
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.ApiStatus

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

@ApiStatus.Internal
fun findPathVariants(mappings: Iterable<PathMapping>,
                     sourcePath: String,
                     sourcePathFun: (PathMapping) -> String,
                     sourceFileSeparator: Char,
                     destPathFun: (PathMapping) -> String,
                     destFileSeparator: Char): List<String> {
  return mappings.mapNotNull { mapping ->
    val sourceBase = sourcePathFun(mapping)
    if (isAncestor(sourceBase, sourcePath, sourceFileSeparator)) {
      val destBase = destPathFun(mapping)
      FileUtil.getRelativePath(sourceBase, sourcePath, sourceFileSeparator)?.let { relativeSourcePath ->
        val relativeDestPath = relativeSourcePath.replaceFileSeparator(sourceFileSeparator, destFileSeparator)
        joinTargetPaths(destBase, relativeDestPath, fileSeparator = destFileSeparator)
      }
    }
    else {
      null
    }
  }
}

private fun isAncestor(ancestor: String, file: String, fileSeparator: Char): Boolean =
  if (fileSeparator == '\\') {
    FileUtil.isAncestor(FileUtil.toSystemIndependentName(ancestor), FileUtil.toSystemIndependentName(file), false)
  }
  else {
    FileUtil.isAncestor(ancestor, file, false)
  }

private fun String.replaceFileSeparator(currentFileSeparator: Char, newFileSeparator: Char): String =
  if (currentFileSeparator == newFileSeparator) this else replace(currentFileSeparator, newFileSeparator)

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
  if (fileSeparator == '\\' && startsWith("\\\\")) {
    // keep leading double back slashes for Windows UNC paths (f.e. for WSL paths that starts with "\\wsl$" prefix)
    "\\" + replace("$fileSeparator$fileSeparator", fileSeparator.toString())
  }
  else {
    replace("$fileSeparator$fileSeparator", fileSeparator.toString())
  }

private fun String.normalizeRelativePath(fileSeparator: Char): String =
  when {
    length == 1 && this[0] == '.' -> ""
    startsWith(prefix = ".$fileSeparator") -> substring(startIndex = 2)
    else -> this
  }.removeSuffix(fileSeparator.toString())

private fun String.ensureEndsWithFileSeparator(fileSeparator: Char): String = if (endsWith(fileSeparator)) this else "$this$fileSeparator"