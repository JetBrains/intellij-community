// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dependencytoolwindow

import com.intellij.openapi.client.ClientProjectSession
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.viewModel.extraction.ToolWindowExtractorMode
import com.intellij.ui.viewModel.extraction.ToolWindowViewModelExtractor

class DependencyToolWindowViewModelExtractor : ToolWindowViewModelExtractor {
  override fun isApplicable(toolWindowId: String, session: ClientProjectSession): Boolean {
    return toolWindowId == ToolWindowId.BUILD_DEPENDENCIES && !session.isOwner
  }

  override fun getMode() = ToolWindowExtractorMode.UNSUPPORTED
}