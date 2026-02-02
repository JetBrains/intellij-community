// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
@ApiStatus.Internal
object ProblemsViewHighlightingChildrenBuilder {
  /**
   * Providers the tree of problems for the given file.
   * Each problem has an associated context (or null if the problem is valid for all contexts) and tool id.
   *   - If there is more than one context group (excluding the global group), the problems are grouped by context first.
   *   - If grouping by tool id is enabled, the problems are grouped by tool id next.
   */
  fun prepareChildrenForFileRoot(
    fileProblems: List<HighlightingProblem>,
    node: FileNode,
    groupByToolId: Boolean,
  ): List<Node> {
    val problemsPerContextGroup = fileProblems.groupBy { it.contextGroup }

    if (problemsPerContextGroup.hasSeveralContextGroups()) {
      // grouping by context first. On the next level, problems are grouped by tool id if enabled.
      return getChildrenGroupedByContext(node, problemsPerContextGroup, groupByToolId)
    }

    // skipping context grouping if single context
    return prepareChildrenWithToolIdGroupingIfEnabled(
      problems = fileProblems,
      groupByToolId = groupByToolId,
      parent = node,
      virtualFile = node.file,
      groupNodeBuilder = { problems, group, parent -> ProblemsViewGroupNode(parent, group, problems) }
    )
  }

  internal fun <ParentNode : Node, GroupNode: Node> prepareChildrenWithToolIdGroupingIfEnabled(
      problems: Collection<Problem>,
      groupByToolId: Boolean,
      parent: ParentNode,
      virtualFile: VirtualFile,
      groupNodeBuilder: (problems: Collection<Problem>, group: String, parent: ParentNode) -> GroupNode,
  ): List<Node> {
    if (!groupByToolId) {
      return problems.toProblemNodes(parent, virtualFile)
    }

    return problems
      .groupBy { it.group }
      .flatMap { (group, problems) ->
        if (group != null) {
          listOf(groupNodeBuilder(problems, group, parent))
        }
        else {
          problems.toProblemNodes(parent, virtualFile)
        }
      }
  }

  private fun getChildrenGroupedByContext(
      node: FileNode,
      problemsPerContextGroup: Map<CodeInsightContext?, List<HighlightingProblem>>,
      groupByToolId: Boolean,
  ): List<Node> = problemsPerContextGroup.flatMap { (context, problems) ->
      when (context) {
          null -> {
              // global group, showing children on top level
              problems.toProblemNodes(parent = node, virtualFile = node.file)
          }

          else -> {
              // showing a singling context group node
              listOf(
                  ProblemsContextNode(
                      parent = node,
                      contextGroup = context,
                      problems = problems,
                      groupByToolId = groupByToolId
                  )
              )
          }
      }
  }

  private fun Map<CodeInsightContext?, List<HighlightingProblem>>.hasSeveralContextGroups(): Boolean {
    val containsGlobalGroup = contains(null)
    val containsSeveralContextGroups = if (containsGlobalGroup) size > 2 else size > 1
    return containsSeveralContextGroups
  }

  internal fun Collection<Problem>.toProblemNodes(
    parent: Node,
    virtualFile: VirtualFile,
  ): List<ProblemNode> =
    map { p ->
      ProblemNode(
        parent = parent,
        file = virtualFile,
        problem = p
      )
    }
}
