// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build

import com.intellij.openapi.util.io.FileUtil
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.invariantSeparatorsPathString

internal fun antToRegex(pattern: String): Regex {
  return pattern
    .let { FileUtil.toSystemIndependentName(it) }
    .let { FileUtil.convertAntToRegexp(it) }
    .let { Regex(it) }
}

class FileSet(private val root: Path) {
  private val includePatterns = mutableMapOf<String, Regex>()
  private val excludePatterns = mutableMapOf<String, Regex>()

  fun include(pattern: String): FileSet {
    includePatterns.put(pattern, antToRegex(pattern))
    return this
  }

  fun includeAll(): FileSet = include("**")

  fun exclude(pattern: String): FileSet {
    excludePatterns.put(pattern, antToRegex(pattern))
    return this
  }

  fun copyToDir(targetDir: Path) {
    for (path in enumerate()) {
      val destination = targetDir.resolve(root.relativize(path))
      Span.current().addEvent("copy", Attributes.of(
        AttributeKey.stringKey("source"), path.toString(),
        AttributeKey.stringKey("destination"), destination.toString(),
      ))
      Files.createDirectories(destination.parent)
      Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
    }
  }

  fun delete() {
    for (path in enumerate()) {
      Span.current().addEvent("delete", Attributes.of(
        AttributeKey.stringKey("files"), path.toString(),
      ))
      Files.delete(path)
    }
  }

  fun enumerate(): List<Path> = toPathListImpl(assertUnusedPatterns = true)

  private fun enumerateNoAssertUnusedPatterns(): List<Path> = toPathListImpl(assertUnusedPatterns = false)

  private fun toPathListImpl(assertUnusedPatterns: Boolean): List<Path> {
    if (includePatterns.isEmpty()) {
      // prevent accidental coding errors, do not remove
      error("No include patterns in $this. Please add some or call includeAll()")
    }

    val usedIncludePatterns = HashSet<String>()
    val usedExcludePatterns = HashSet<String>()
    val result = ArrayList<Path>()
    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        val relative = root.relativize(file)

        var included = false
        for ((pattern, pathMatcher) in includePatterns) {
          if (pathMatcher.matches(relative.invariantSeparatorsPathString)) {
            included = true
            usedIncludePatterns.add(pattern)
          }
        }

        if (!included) {
          return FileVisitResult.CONTINUE
        }

        var excluded = false
        for ((pattern, pathMatcher) in excludePatterns) {
          if (pathMatcher.matches(relative.invariantSeparatorsPathString)) {
            excluded = true
            usedExcludePatterns.add(pattern)
          }
        }

        if (!excluded) {
          result.add(file)
        }

        return FileVisitResult.CONTINUE
      }
    })

    val unusedIncludePatterns = includePatterns.keys - usedIncludePatterns
    if (assertUnusedPatterns && unusedIncludePatterns.isNotEmpty()) {
      // prevent accidental coding errors, do not remove
      error("The following include patterns were not matched while traversing $this:\n" + unusedIncludePatterns.joinToString("\n"))
    }

    val unusedExcludePatterns = excludePatterns.keys - usedExcludePatterns
    if (assertUnusedPatterns && unusedExcludePatterns.isNotEmpty()) {
      // Prevent accidental coding errors, do not remove
      error("The following exclude patterns were not matched while traversing $this:\n" + unusedExcludePatterns.joinToString("\n"))
    }

    return result
  }

  fun isEmpty(): Boolean = enumerateNoAssertUnusedPatterns().isEmpty()

  override fun toString(): String {
    return "FileSet(" +
           "root='$root', " +
           "included=[${includePatterns.keys.sorted().joinToString(", ")}], " +
           "excluded=[${excludePatterns.keys.sorted().joinToString(", ")}]" +
           ")"
  }
}
