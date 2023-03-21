// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dependencytoolwindow

import com.intellij.codeWithMe.ClientId
import com.intellij.ui.viewModel.extraction.ToolWindowExtractorMode
import com.intellij.ui.viewModel.extraction.ToolWindowViewModelExtractor

class DependencyToolWindowViewModelExtractor : ToolWindowViewModelExtractor {
  override fun isApplicable(toolWindowId: String, clientId: ClientId?) =
    toolWindowId == DEPENDENCIES_TOOL_WINDOW_ID && clientId != ClientId.ownerId

  override fun getMode() = ToolWindowExtractorMode.UNSUPPORTED
}