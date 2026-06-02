// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.util.PathUtil
import org.eclipse.lsp4j.WorkspaceSymbol
import javax.swing.Icon

/**
 * Navigation item for LSP Workspace Symbol. Construct with server and original WorkspaceSymbol.
 */
data class LspWorkspaceSymbolNavigationItem(
  private val lspClient: LspClient,
  private val workspaceSymbol: WorkspaceSymbol,
) : NavigationItem {

  @Deprecated("Use the LspClient constructor")
  @Suppress("DEPRECATION")
  constructor(lspServer: LspServer, workspaceSymbol: WorkspaceSymbol) : this(lspServer as LspClient, workspaceSymbol)

  override fun getName(): String = workspaceSymbol.name

  override fun getPresentation(): ItemPresentation {
    val symbolKindCustomizer = lspClient.descriptor.lspCustomization.symbolKindCustomizer
    val icon: Icon? = symbolKindCustomizer.getIcon(workspaceSymbol.kind)
    return object : ItemPresentation {
      override fun getPresentableText(): String = name
      override fun getLocationString(): String = PathUtil.getFileName(workspaceSymbol.location.left?.uri
                                                                      ?: workspaceSymbol.location.right?.uri ?: "")

      override fun getIcon(unused: Boolean): Icon? = icon
    }
  }

  override fun navigate(requestFocus: Boolean) {
    val virtualFile = resolveVirtualFile()
    if (virtualFile == null) return
    val range = workspaceSymbol.location.left?.range
    val project = lspClient.project
    if (range != null) {
      val document = FileDocumentManager.getInstance().getDocument(virtualFile)
      val startOffset = document?.let { getOffsetInDocument(it, range.start) } ?: 0
      FileEditorManager.getInstance(project).openTextEditor(
        OpenFileDescriptor(project, virtualFile, startOffset),
        requestFocus
      )
    }
    else {
      FileEditorManager.getInstance(project).openFile(virtualFile, requestFocus)
    }
  }

  override fun canNavigate(): Boolean = true

  override fun canNavigateToSource(): Boolean = canNavigate()

  private fun resolveVirtualFile(): VirtualFile? {
    val uri = workspaceSymbol.location.left?.uri ?: workspaceSymbol.location.right?.uri
    return uri?.let { lspClient.descriptor.findFileByUri(it) }
  }
}