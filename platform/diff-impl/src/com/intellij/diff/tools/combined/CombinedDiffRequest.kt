// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import org.jetbrains.annotations.Nls

class CombinedDiffRequest(private val title: @Nls String?, requests: List<ChildDiffRequest>) : DiffRequest() {

  private val _requests: MutableList<ChildDiffRequest>

  init {
    _requests = requests.toMutableList()
  }

  class ChildDiffRequest(val request: DiffRequest, val path: FilePath, val fileStatus: FileStatus)

  data class NewChildDiffRequestData(val path: FilePath, val fileStatus: FileStatus, val position: InsertPosition)

  data class InsertPosition(val path: FilePath, val fileStatus: FileStatus, val above: Boolean)

  fun getChildRequests() = _requests.toList()

  fun addChild(childRequest: ChildDiffRequest, position: InsertPosition) {
    val above = position.above
    val existingIndex = _requests.indexOfFirst { request -> request.path == position.path && request.fileStatus == position.fileStatus }

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

  override fun onAssigned(isAssigned: Boolean) {
    for (request in _requests) {
      request.request.onAssigned(isAssigned)
    }
  }

  override fun toString(): String {
    return super.toString() + ":" + title
  }
}
