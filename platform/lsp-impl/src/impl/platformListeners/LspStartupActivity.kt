package com.intellij.platform.lsp.impl.platformListeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.impl.LspClientManagerImpl

/**
 * Editors restored on project reopen fire [fileOpened][LspFileEditorManagerListener.fileOpened] before the project file index
 * knows the content roots, so those events start no LSP servers. Re-process the open editors once the project is fully loaded.
 */
internal class LspStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!LspIntegrationProvider.hasAnyExtensions()) return
    LspClientManagerImpl.getInstanceImpl(project).onProjectRootsChanged()
  }
}
