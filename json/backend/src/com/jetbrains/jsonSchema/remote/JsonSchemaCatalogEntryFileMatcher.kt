// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.JsonSchemaCatalogEntry
import java.nio.file.FileSystems
import java.nio.file.InvalidPathException
import java.nio.file.PathMatcher
import java.nio.file.Paths
import java.util.regex.PatternSyntaxException

class JsonSchemaCatalogEntryFileMatcher(val entry: JsonSchemaCatalogEntry) {
  private var matcher: PathMatcher? = null

  fun matches(file: VirtualFile, project: Project): Boolean {
    val relativePath = getRelativePath(file, project)
    if (matches(relativePath)) {
      return true
    }

    val fileName = file.name
    return fileName != relativePath && matches(fileName)
  }

  private fun matches(filePath: String?): Boolean {
    if (filePath == null) return false

    val path = try {
      Paths.get(filePath)
    }
    catch (e: InvalidPathException) {
      LOG.debug("Unable to process invalid path '$filePath'", e)
      return false
    }

    return try {
      if (matcher == null) {
        matcher = buildPathMatcher(entry.fileMasks)
      }
      matcher!!.matches(path)
    }
    catch (e: PatternSyntaxException) {
      LOG.warn("Unable to process matches for path '$path' with matcher URL '${entry.url}'", e)
      false
    }
  }

  companion object {
    private val LOG = Logger.getInstance(JsonSchemaCatalogEntryFileMatcher::class.java)
    private const val MAX_OPTIONAL_SEGMENT_COUNT = 4

    @JvmStatic
    fun getRelativePath(file: VirtualFile, project: Project): String? {
      var basePath = project.basePath
      if (basePath != null) {
        basePath = StringUtil.trimEnd(basePath, VfsUtilCore.VFS_SEPARATOR_CHAR) + VfsUtilCore.VFS_SEPARATOR_CHAR
        val filePath = file.path
        if (filePath.startsWith(basePath)) {
          return filePath.substring(basePath.length)
        }
      }

      val contentRoot = ReadAction.compute<VirtualFile?, RuntimeException> {
        if (project.isDisposed || !file.isValid) return@compute null
        ProjectFileIndex.getInstance(project).getContentRootForFile(file, false)
      }
      return contentRoot?.let { VfsUtilCore.findRelativePath(it, file, VfsUtilCore.VFS_SEPARATOR_CHAR) }
    }

    private fun buildPathMatcher(fileMasks: Collection<String>): PathMatcher {
      val refinedFileMasks = fileMasks.flatMapTo(LinkedHashSet()) { expandDirectoryWildcards(it) }
      // one matcher per mask, because a mask can contain a group, and a Java glob cannot nest groups
      val matchers = refinedFileMasks.map { FileSystems.getDefault().getPathMatcher("glob:$it") }
      return PathMatcher { path -> matchers.any { it.matches(path) } }
    }

    /**
     * Returns [fileMask] and its variants without one or more `**` directory segments.
     * In a catalog mask, a `**` directory segment matches zero or more directories.
     * In a Java glob, it matches one or more directories.
     * A mask with more than [MAX_OPTIONAL_SEGMENT_COUNT] such segments stays as it is.
     */
    private fun expandDirectoryWildcards(fileMask: String): List<String> {
      val segments = fileMask.split('/')
      val optionalSegments = segments.indices.filter { segments[it] == "**" && it < segments.lastIndex }
      if (optionalSegments.isEmpty() || optionalSegments.size > MAX_OPTIONAL_SEGMENT_COUNT) return listOf(fileMask)

      var variants = listOf(emptyList<String>())
      for ((index, segment) in segments.withIndex()) {
        val withSegment = variants.map { it + segment }
        variants = if (index in optionalSegments) withSegment + variants else withSegment
      }
      return variants.map { it.joinToString("/") }
    }
  }
}
