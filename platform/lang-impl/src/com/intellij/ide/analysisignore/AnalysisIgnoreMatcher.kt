// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetExclusionCondition
import org.jetbrains.annotations.ApiStatus

/**
 * The patterns of one [`.analysisignore`][ANALYSIS_IGNORE_FILE_NAME] file, as a condition of the index.
 */
@ApiStatus.Internal
class AnalysisIgnoreMatcher(
  private val baseDirUrl: String,
  private val baseDir: VirtualFile?,
  private val patterns: List<AnalysisIgnorePattern>,
  private val caseSensitive: Boolean,
) : WorkspaceFileSetExclusionCondition {

  // The lines that these patterns come from. The index compares two conditions by these lines.
  private val sources: List<String> = patterns.map { it.source }

  // The patterns without a slash. Each names a name at any level below the directory of the file.
  private val namePatterns: List<AnalysisIgnorePattern> = patterns.filter { !it.anchored }

  // The anchored patterns with a `**`. Each needs the whole path of a file below the directory of the file.
  private val chainPatterns: List<AnalysisIgnorePattern> = patterns.filter { it.anchored && it.hasAnySegments }

  // The anchored patterns without a `**`, at the index of their depth. Such a pattern matches a path of its depth only.
  private val patternsByDepth: Array<List<AnalysisIgnorePattern>>

  // The most steps to take from a file up before the count says that no anchored pattern matches. The chain patterns need every step.
  private val depthLimit: Int

  init {
    val fixedDepthPatterns = patterns.filter { it.anchored && !it.hasAnySegments }
    val maxDepth = fixedDepthPatterns.maxOfOrNull { it.depth } ?: 0
    patternsByDepth = Array(maxDepth + 1) { depth -> fixedDepthPatterns.filter { it.depth == depth } }
    depthLimit = if (chainPatterns.isEmpty()) maxDepth else Int.MAX_VALUE
  }

  override fun shouldExclude(file: VirtualFile): Boolean {
    if (file == baseDir) return false

    var node: AnalysisIgnoreNode? = null
    if (namePatterns.isNotEmpty()) {
      node = AnalysisIgnoreNode(file)
      for (i in namePatterns.indices) {
        if (namePatterns[i].matchesName(node)) return true
      }
    }
    if (baseDir == null || depthLimit == 0) return false

    val depth = depthBelow(file)
    if (depth < 0) return false
    if (node == null) node = AnalysisIgnoreNode(file)
    if (depth < patternsByDepth.size) {
      val candidates = patternsByDepth[depth]
      for (i in candidates.indices) {
        if (candidates[i].matchesUpward(node)) return true
      }
    }
    if (chainPatterns.isNotEmpty()) {
      val chain = chainOf(node, depth)
      for (i in chainPatterns.indices) {
        if (chainPatterns[i].matchesChain(chain)) return true
      }
    }
    return false
  }

  private fun depthBelow(file: VirtualFile): Int {
    var depth = 0
    var current = file
    while (depth < depthLimit) {
      val parent = current.parent
      if (parent == null || parent == current) return -1
      depth++
      if (parent == baseDir) return depth
      current = parent
    }
    return -1
  }

  /**
   * Returns the files from the one right below [baseDir] down to the file of [node], which lies [depth] steps below [baseDir].
   */
  private fun chainOf(node: AnalysisIgnoreNode, depth: Int): List<AnalysisIgnoreNode> {
    val chain = ArrayList<AnalysisIgnoreNode>(depth)
    chain.add(node)
    var current = node.file
    while (chain.size < depth) {
      current = current.parent ?: break
      chain.add(AnalysisIgnoreNode(current))
    }
    return chain.asReversed()
  }

  /**
   * Returns `true` if the patterns exclude the path. [relativePath] is the path below the directory of the file, with a `/` between its
   * components, and [name] is its last component.
   */
  fun isExcluded(relativePath: CharSequence, name: CharSequence, isDirectory: Boolean): Boolean {
    // The directory of the file itself. No pattern of the file names it.
    if (relativePath.isEmpty()) return false

    for (i in patterns.indices) {
      if (patterns[i].matches(relativePath, name, isDirectory)) return true
    }
    return false
  }

  override fun equals(other: Any?): Boolean {
    return other is AnalysisIgnoreMatcher &&
           baseDirUrl == other.baseDirUrl &&
           baseDir == other.baseDir &&
           caseSensitive == other.caseSensitive &&
           sources == other.sources
  }

  override fun hashCode(): Int {
    var result = baseDirUrl.hashCode()
    result = 31 * result + baseDir.hashCode()
    result = 31 * result + caseSensitive.hashCode()
    result = 31 * result + sources.hashCode()
    return result
  }
}
