// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.structureView

import com.intellij.ide.impl.StructureViewWrapperImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerManagerListener
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.util.application

internal class LspStructureViewProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    LspServerManager.getInstance(project).addLspServerManagerListener(
      listener = object : LspServerManagerListener {
        override fun serverStateChanged(lspServer: LspServer) {
          if (lspServer.state == LspServerState.Running ||
              lspServer.state == LspServerState.ShutdownNormally ||
              lspServer.state == LspServerState.ShutdownUnexpectedly) {
            refreshStructureView()
          }
        }

        override fun fileOpened(lspServer: LspServer, file: VirtualFile) {
          refreshStructureView()
        }
      },
      parentDisposable = project,
      sendEventsForExistingServers = true,
    )
  }

  private fun refreshStructureView() {
    ApplicationManager.getApplication().invokeLater {
      application.messageBus.syncPublisher(StructureViewWrapperImpl.STRUCTURE_CHANGED).run()
    }
  }
}
