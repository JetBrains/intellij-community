// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dependencytoolwindow

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.viewModel.extraction.ToolWindowExtractorMode
import com.intellij.ui.viewModel.extraction.ToolWindowViewModelExtractor

class DependencyToolWindowViewModelExtractor : ToolWindowViewModelExtractor {
  override fun isApplicable(toolWindowId: String, clientId: ClientId?) =
    toolWindowId == ToolWindowId.BUILD_DEPENDENCIES && clientId != ClientId.ownerId

  override fun getMode() = ToolWindowExtractorMode.UNSUPPORTED
}