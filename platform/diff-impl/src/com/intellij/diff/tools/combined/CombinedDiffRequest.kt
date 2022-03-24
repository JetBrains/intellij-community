// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls

class CombinedDiffRequest(private val title: @Nls String?, requests: List<ChildDiffRequest>) : DiffRequest() {

  private val _requests: MutableList<ChildDiffRequest>

  init {
    _requests = requests.toMutableList()
  }

  class ChildDiffRequest(val producer: DiffRequestProducer, val blockId: CombinedBlockId)

  data class NewChildDiffRequestData(val blockId: CombinedBlockId, val position: InsertPosition)

  data class InsertPosition(val blockId: CombinedBlockId, val above: Boolean)

  fun getChildRequest(index: Int) = _requests.getOrNull(index)

  fun getChildRequests() = _requests.toList()

  fun getChildRequestsSize() = _requests.size

  fun addChild(childRequest: ChildDiffRequest, position: InsertPosition) {
    val above = position.above
    val existingIndex = _requests.indexOfFirst { request -> request.blockId == position.blockId }

    if (existingIndex != -1) {
      val newIndex = if (above) existingIndex else existingIndex + 1
      _requests.add(newIndex, childRequest)
    }
    else {
      _requests.add(childRequest)
    }
  }

  fun removeChild(childRequest: ChildDiffRequest) {
    _requests.remove(childRequest)
  }

  override fun getTitle(): @NlsContexts.DialogTitle String? {
    return title
  }

  override fun toString(): String {
    return super.toString() + ":" + title
  }
}
