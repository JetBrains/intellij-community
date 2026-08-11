package com.intellij.platform.lsp.impl.serviceView

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.execution.filters.HyperlinkInfoBase
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.VisibleForTesting

private val PRETTY_GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

@VisibleForTesting
internal fun buildPayloadPopupText(json: String, truncated: Boolean): @NlsSafe String {
  val pretty = runCatching { PRETTY_GSON.toJson(JsonParser.parseString(json)) }.getOrNull() ?: json
  return if (truncated) "$pretty\n\n${LspBundle.message("services.lsp.traffic.popup.payload.truncated")}" else pretty
}

/**
 * Attached to the header of an `IN`/`OUT` traffic line printed by [LspClientConsole.printTraffic].
 * On click, shows a popup with the pretty-printed JSON-RPC payload at the click location.
 *
 * The payload is stored raw and formatted lazily on click, so no JSON formatting happens on the LSP protocol threads.
 */
internal class LspTrafficPayloadHyperlinkInfo(
  internal val header: @NlsSafe String,
  internal val json: String,
  internal val truncated: Boolean,
) : HyperlinkInfoBase() {

  override fun navigate(project: Project, hyperlinkLocationPoint: RelativePoint?) {
    val text = buildPayloadPopupText(json, truncated)

    val textArea = JBTextArea(text).apply {
      isEditable = false
      font = EditorUtil.getEditorFont()
      caretPosition = 0
    }
    val scrollPane = JBScrollPane(textArea).apply {
      preferredSize = JBUI.size(700, 400)
      border = JBUI.Borders.empty()
    }

    val popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(scrollPane, textArea)
      .setTitle(header)
      .setResizable(true)
      .setMovable(true)
      .setRequestFocus(true)
      .createPopup()

    if (hyperlinkLocationPoint != null) {
      popup.show(hyperlinkLocationPoint)
    }
    else {
      popup.showCenteredInCurrentWindow(project)
    }
  }
}
