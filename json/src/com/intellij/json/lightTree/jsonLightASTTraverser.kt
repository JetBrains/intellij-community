// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.lightTree

import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class LightTreeSearchParameters(val acceptedNodeTypes: TokenSet,
                                val shouldPrependParentIdToCurrentNodeId: Boolean,
                                val nodeIdentifierExtractor: (LighterAST, LighterASTNode) -> String?)

@Internal
data class LightNodeIdAndOffset(val identifier: String, val offset: Int)

@Internal
fun queryLightAST(tree: LighterAST, searchParameters: LightTreeSearchParameters): Sequence<LightNodeIdAndOffset> = sequence {
  extractNodeDataRecursive(tree, tree.root, searchParameters, "")
}

private suspend fun SequenceScope<LightNodeIdAndOffset>.extractNodeDataRecursive(tree: LighterAST, node: LighterASTNode, searchParameters: LightTreeSearchParameters, prefix: String) {
  ProgressManager.checkCanceled()
  val nodeData = extractNodeDataIfApplicable(tree, node, prefix, searchParameters)
  if (nodeData != null) {
    yield(nodeData)
  }

  val effectivePrefix = nodeData?.identifier ?: prefix
  tree.getChildren(node)
    .forEach { childNode -> extractNodeDataRecursive(tree, childNode, searchParameters, effectivePrefix) }
}

private fun extractNodeDataIfApplicable(tree: LighterAST, node: LighterASTNode, prefix: String, searchParameters: LightTreeSearchParameters): LightNodeIdAndOffset? {
  if (!searchParameters.acceptedNodeTypes.contains(node.tokenType)) return null

  val currentNodeId = searchParameters.nodeIdentifierExtractor(tree, node) ?: return null
  val currentNodeFqn = if (prefix.isNotEmpty() && searchParameters.shouldPrependParentIdToCurrentNodeId) {
    "$prefix.$currentNodeId"
  }
  else {
    currentNodeId
  }
  val offset = node.startOffset

  return LightNodeIdAndOffset(currentNodeFqn, offset)
}