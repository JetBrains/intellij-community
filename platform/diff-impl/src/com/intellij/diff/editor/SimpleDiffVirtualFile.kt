// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt

class SimpleDiffVirtualFile(val request: DiffRequest) : DiffVirtualFile(DiffBundle.message("label.default.diff.editor.tab.name")) {
  override fun getName(): String = request.title ?: super.getName()

  override fun createProcessor(project: Project): DiffRequestProcessor = MyDiffRequestProcessor(project, request)

  override fun toString(): String = "${super.toString()}:$request"

  private class MyDiffRequestProcessor(
    project: Project?,
    val request: DiffRequest
  ) : DiffRequestProcessor(project) {

    @RequiresEdt
    @Synchronized
    override fun updateRequest(force: Boolean, scrollToChangePolicy: DiffUserDataKeysEx.ScrollToPolicy?) {
      applyRequest(request, force, scrollToChangePolicy)
    }
  }
}
