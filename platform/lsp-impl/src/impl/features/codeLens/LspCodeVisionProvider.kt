package com.intellij.platform.lsp.impl.features.codeLens

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionState
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.customization.LspCodeLensSupport
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import java.awt.event.MouseEvent

internal const val LSP_CODE_VISION_PROVIDER_ID: String = "LspCodeVisionProvider"

internal class LspCodeVisionProvider : CodeVisionProvider<Unit>, DumbAware {
  override fun precomputeOnUiThread(editor: Editor) {}

  override fun computeCodeVision(
    editor: Editor,
    uiData: Unit,
  ): CodeVisionState {
    val project = editor.project ?: return CodeVisionState.Ready(emptyList())
    val document = editor.document
    val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return CodeVisionState.Ready(emptyList())
    if (virtualFile is VirtualFileWindow || !virtualFile.isInLocalFileSystem) return CodeVisionState.Ready(emptyList())

    val clients = LspClientManagerImpl.getInstanceImpl(project).getClientsWithThisFileOpen(virtualFile)

    val lenses = clients.flatMap { client ->
        client.getCodeLens(virtualFile).map { it to client }
      }

    if (lenses.isEmpty()) return CodeVisionState.Ready(emptyList())

    val entries = ArrayList<Pair<TextRange, CodeVisionEntry>>()

    for ((cachedLens, client) in lenses) {
      val lens = cachedLens.highlightingInfo
      val customizer = client.descriptor.lspCustomization.codeLensCustomizer as LspCodeLensSupport
      if (!customizer.shouldDisplayCodeLens(virtualFile, lens)) continue
      val command = lens.command ?: continue
      val title = command.title ?: continue


      val handler = { mouseEvent: MouseEvent?, _: Editor ->
        customizer.codeLensClicked(client as LspClient, virtualFile, command, mouseEvent)
      }
      val entry = ClickableTextCodeVisionEntry(title, id, handler)

      entries.add(cachedLens.textRange to entry)
    }

    return CodeVisionState.Ready(entries)
  }

  override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Top
  override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()
  override val id: String = LSP_CODE_VISION_PROVIDER_ID
  override val name: String = LspBundle.message("codeLens.LspCodeVisionProvider.name")
  override val singleEntryPerLine: Boolean = false
}
