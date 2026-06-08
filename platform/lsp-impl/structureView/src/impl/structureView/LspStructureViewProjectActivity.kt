// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.structureView

import com.intellij.ide.impl.StructureViewWrapperImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.util.application

internal class LspStructureViewProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    LspClientManager.getInstance(project).addListener(
      listener = object : LspClientManagerListener {
        override fun serverStateChanged(lspClient: LspClient) {
          if (lspClient.state == LspServerState.Running ||
              lspClient.state == LspServerState.ShutdownNormally ||
              lspClient.state == LspServerState.ShutdownUnexpectedly) {
            refreshStructureView()
          }
        }

        override fun fileOpened(lspClient: LspClient, file: VirtualFile) {
          refreshStructureView()
        }
      },
      parentDisposable = project,
      sendEventsForExistingClients = true,
    )
  }

  private fun refreshStructureView() {
    ApplicationManager.getApplication().invokeLater {
      application.messageBus.syncPublisher(StructureViewWrapperImpl.STRUCTURE_CHANGED).run()
    }
  }
}
