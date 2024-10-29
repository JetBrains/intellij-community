// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.nodes

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.todo.HighlightedRegionProvider
import com.intellij.ui.HighlightedRegion
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TodoItemNodePresentationData : PresentationData() {

  var additionalLines: MutableList<HighlightedRegionProvider> = ContainerUtil.createConcurrentList()
    private set
  var highlightedRegions: MutableList<HighlightedRegion> = ContainerUtil.createConcurrentList()
    private set

  override fun clone(): PresentationData {
    val clone = super.clone() as TodoItemNodePresentationData
    clone.additionalLines = ContainerUtil.createConcurrentList(additionalLines)
    clone.highlightedRegions = ContainerUtil.createConcurrentList(highlightedRegions)
    return clone
  }

  override fun getEqualityObjects(): Array<Any?> {
    val superEqualityObjects = super.getEqualityObjects()
    val result = Array(superEqualityObjects.size + 2) { i -> superEqualityObjects.getOrNull(i) }
    result[superEqualityObjects.size] = additionalLines
    result[superEqualityObjects.size + 1] = highlightedRegions
    return result
  }

  override fun copyFrom(from: PresentationData) {
    super.copyFrom(from)
    if (from !is TodoItemNodePresentationData) {
      return
    }
    additionalLines.clear()
    additionalLines.addAll(from.additionalLines)
    highlightedRegions.clear()
    highlightedRegions.addAll(from.highlightedRegions)
  }

  override fun applyFrom(from: PresentationData) {
    super.applyFrom(from)
    if (from !is TodoItemNodePresentationData) {
      return
    }
    if (additionalLines.isEmpty()) {
      additionalLines.addAll(from.additionalLines)
    }
    if (highlightedRegions.isEmpty()) {
      highlightedRegions.addAll(from.highlightedRegions)
    }
  }

}
