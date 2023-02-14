// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.io.FileUtil
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.function.BiPredicate
import java.util.stream.Collectors

class FileSet(private val root: Path) {
  private val includePatterns = mutableMapOf<String, Regex>()
  private val excludePatterns = mutableMapOf<String, Regex>()

  private fun toRegex(pattern: String): Regex = pattern
    .let { FileUtil.toSystemIndependentName(it) }
    .let { FileUtil.convertAntToRegexp(it) }
    .let { Regex(it) }

  fun include(pattern: String): FileSet {
    includePatterns[pattern] = toRegex(pattern)
    return this
  }

  fun includeAll(): FileSet = include("**")

  fun exclude(pattern: String): FileSet {
    excludePatterns[pattern] = toRegex(pattern)
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

  fun enumerateNoAssertUnusedPatterns(): List<Path> = toPathListImpl(assertUnusedPatterns = false)

  private fun toPathListImpl(assertUnusedPatterns: Boolean): List<Path> {
    if (includePatterns.isEmpty()) {
      // Prevent accidental coding errors, do not remove
      error("No include patterns in $this. Please add some or call includeAll()")
    }

    val usedIncludePatterns = mutableSetOf<String>()
    val usedExcludePatterns = mutableSetOf<String>()
    val result = Files.find(root, Int.MAX_VALUE, BiPredicate { path, attr ->
      if (attr.isDirectory) {
        return@BiPredicate false
      }

      val relative = root.relativize(path)

      var included = false
      for ((pattern, pathMatcher) in includePatterns) {
        if (pathMatcher.matches(FileUtil.toSystemIndependentName(relative.toString()))) {
          included = true
          usedIncludePatterns.add(pattern)
        }
      }

      if (!included) {
        return@BiPredicate false
      }

      var excluded = false
      for ((pattern, pathMatcher) in excludePatterns) {
        if (pathMatcher.matches(FileUtil.toSystemIndependentName(relative.toString()))) {
          excluded = true
          usedExcludePatterns.add(pattern)
        }
      }

      return@BiPredicate !excluded
    }).use { stream ->
      stream.collect(Collectors.toList())
    }

    val unusedIncludePatterns = includePatterns.keys - usedIncludePatterns
    if (assertUnusedPatterns && unusedIncludePatterns.isNotEmpty()) {
      // Prevent accidental coding errors, do not remove
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

  override fun toString() = "FileSet(" +
                            "root='$root', " +
                            "included=[${includePatterns.keys.sorted().joinToString(", ")}], " +
                            "excluded=[${excludePatterns.keys.sorted().joinToString(", ")}]" +
                            ")"
}
