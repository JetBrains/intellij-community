package com.intellij.platform.lsp.impl.features.inlayCommon

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiFile
import javax.swing.JComponent
import javax.swing.JPanel

private val lspInlayHintsKey: SettingsKey<NoSettings> = SettingsKey("lsp.inlay.hints")

/**
 * The single daemon-pass trigger for all LSP out-of-band inline inlays. The daemon runs this per editor (including
 * splits); it schedules [LspInlayApplier], which collects and paints BOTH inlay hints and document-color swatches.
 */
internal class LspInlayHintsProvider : InlayHintsProvider<NoSettings>, DumbAware {

  override val isVisibleInSettings: Boolean = false

  override val name: String = "LSP-based inlays" // doesn't show up in UI

  override val key: SettingsKey<NoSettings> = lspInlayHintsKey

  override fun createSettings(): NoSettings = NoSettings()

  override val previewText: String? = null

  override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
    override fun createComponent(listener: ChangeListener): JComponent = JPanel()
  }

  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector? {
    val virtualFile = file.virtualFile ?: return null
    if (virtualFile is VirtualFileWindow || !virtualFile.isInLocalFileSystem) return null

    // The daemon runs this pass per editor (including splits), so it's our per-editor hook. scheduleRefresh triggers
    // the server request (when the cache is stale) and re-applies the cache to every editor of the file, so a newly
    // opened/split editor gets painted. Returning null keeps the daemon pass from painting, avoiding duplicates.
    LspInlayApplier.getInstance(file.project).scheduleRefresh(virtualFile)
    return null
  }
}
