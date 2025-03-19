// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.logging

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern
import kotlin.io.path.*

/**
 * Return paths to files which matches the given [pathPattern] in Ant format.
 * @param includeAll if `true`, all matching files will be returned, otherwise only the last modified one.
 */
@RequiresBackgroundThread
@OptIn(ExperimentalPathApi::class)
@VisibleForTesting
@ApiStatus.Internal
fun collectLogPaths(pathPattern: String?, includeAll: Boolean): Set<String> {
  if (pathPattern == null) {
    return emptySet()
  }
  
  val logFile = File(pathPattern)
  if (logFile.exists()) {
    return setOf(pathPattern)
  }

  var depth = 0
  var root: File? = logFile.parentFile
  var patternString = logFile.name
  while (root != null && !root.exists() && depth < 5) {
    patternString = "${root.name}/$patternString"
    root = root.parentFile
    depth++
  }
  if (root == null || !root.exists()) {
    return emptySet()
  }

  val pattern = Pattern.compile(FileUtil.convertAntToRegexp(patternString))
  val matchingPaths = ArrayList<Path>()
  root.toPath().visitFileTree(MatchingPathsCollector(pattern, matchingPaths))
  if (matchingPaths.isEmpty()) {
    return emptySet()
  }
  if (includeAll) {
    return matchingPaths.mapTo(LinkedHashSet(matchingPaths.size)) { it.pathString }
  }
  else {
    return setOfNotNull(matchingPaths.maxByOrNull { it.getLastModifiedTime() }?.pathString)
  }
}

private class MatchingPathsCollector(private val pattern: Pattern, private val result: ArrayList<Path>) : FileVisitor<Path> {
  var relativePath = ""
  var initialDir = true

  override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
    if (!initialDir) {
      relativePath = "$relativePath${dir.name}/"
    }
    else {
      initialDir = false
    }
    return FileVisitResult.CONTINUE
  }

  override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
    if (pattern.matcher(relativePath + file.name).matches()) {
      result.add(file)
      if (result.size > 100) {
        return FileVisitResult.TERMINATE
      }
    }
    return FileVisitResult.CONTINUE
  }

  override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
    return FileVisitResult.CONTINUE
  }

  override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
    relativePath = relativePath.removeSuffix("${dir.name}/")
    return FileVisitResult.CONTINUE
  }
}
