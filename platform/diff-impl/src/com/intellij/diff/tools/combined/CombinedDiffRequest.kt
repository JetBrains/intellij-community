// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.combined

import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import org.jetbrains.annotations.Nls

class CombinedDiffRequest(private val title: @Nls String?,
                          internal val requests: List<ChildDiffRequest>) : DiffRequest() {

  class ChildDiffRequest(val request: DiffRequest, val path: FilePath, val fileStatus: FileStatus)

  override fun getTitle(): @NlsContexts.DialogTitle String? {
    return title
  }

  override fun onAssigned(isAssigned: Boolean) {
    for (request in requests) {
      request.request.onAssigned(isAssigned)
    }
  }

  override fun toString(): String {
    return super.toString() + ":" + title
  }
}
