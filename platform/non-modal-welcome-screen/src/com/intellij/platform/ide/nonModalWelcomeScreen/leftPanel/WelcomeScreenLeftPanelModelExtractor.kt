// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel

import com.intellij.openapi.client.ClientProjectSession
import com.intellij.ui.viewModel.extraction.ProjectViewPaneExtractorMode
import com.intellij.ui.viewModel.extraction.ProjectViewPaneModelExtractor

class WelcomeScreenLeftPanelModelExtractor : ProjectViewPaneModelExtractor {
  override fun isApplicable(paneId: String, session: ClientProjectSession): Boolean =
    paneId == WelcomeScreenLeftPanel.ID && session.isController

  override fun getMode(): ProjectViewPaneExtractorMode = ProjectViewPaneExtractorMode.DIRECT_TRANSFER
}