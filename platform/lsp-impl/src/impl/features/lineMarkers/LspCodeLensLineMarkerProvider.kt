// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.lineMarkers

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.customization.LspCodeLensSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.features.codeLens.parseCodeLensTitle
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import org.eclipse.lsp4j.Command
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Shows the allowlisted code lenses of LSP-backed files as gutter action icons.
 *
 * The platform renders a code lens as a Code Vision entry above the line. A run or test lens belongs in the gutter instead,
 * where the IDE shows every other run action, so an integration names those commands in
 * [LspCodeLensSupport.gutterLensCommands]. The default is the empty set, which switches this provider off.
 *
 * The provider only reads the [com.intellij.platform.lsp.impl.features.codeLens.LspCodeLensCache] snapshots.
 * The cache pulls the lenses from the server asynchronously and restarts the daemon when a response arrives, as long as the
 * integration allowlists a command.
 *
 * One line carries one icon: the lenses of one line become one marker, and a click opens the list when there is more than one.
 */
internal class LspCodeLensLineMarkerProvider : LineMarkerProviderDescriptor(), DumbAware {
  override fun getName(): String = LspBundle.message("lineMarkers.LspCodeLensLineMarkerProvider.name")

  override fun getIcon(): Icon = AllIcons.Actions.Execute

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

  override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
    val psiFile = elements.firstOrNull()?.containingFile ?: return
    val virtualFile = psiFile.virtualFile ?: return
    if (virtualFile is VirtualFileWindow) return
    val document = psiFile.viewProvider.document ?: return

    // computed lazily: most files have no LSP client with this feature
    var elementSet: Set<PsiElement>? = null
    val lines = LinkedHashMap<Int, GutterLensLine>()

    for (client in LspClientManagerImpl.getInstanceImpl(psiFile.project).getClientsWithThisFileOpen(virtualFile)) {
      val customizer = client.descriptor.lspCustomization.codeLensCustomizer
      if (customizer !is LspCodeLensSupport) continue
      val commands = customizer.gutterLensCommands
      if (commands.isEmpty()) continue

      for (cached in client.highlightingCacheRegistry.codeLensCache.getHighlightings(virtualFile)) {
        ProgressManager.checkCanceled()
        val command = cached.highlightingInfo.command ?: continue
        if (command.command !in commands) continue
        val title = command.title ?: continue

        // A snapshot range can lag behind the document for a moment, and LineMarkerInfo rejects an out-of-file range.
        val anchorRange = cached.textRange.intersection(psiFile.textRange) ?: continue
        if (anchorRange.startOffset > document.textLength) continue
        val anchor = psiFile.findElementAt(anchorRange.startOffset) ?: continue
        val set = elementSet ?: elements.toHashSet().also { elementSet = it }
        // The contract of collectSlowLineMarkers: create markers only for the passed elements.
        if (anchor !in set) continue

        val line = lines.getOrPut(document.getLineNumber(anchorRange.startOffset)) { GutterLensLine(anchor, anchorRange) }
        line.lenses.add(GutterLens(client, customizer, title, command))
      }
    }

    lines.values.mapTo(result) { it.createLineMarkerInfo(virtualFile) }
  }
}

/** One allowlisted lens of a line: the client that answered it, and the command to run on a click. */
private class GutterLens(
  val lspClient: LspClientImpl,
  val customizer: LspCodeLensSupport,
  title: @NlsSafe String,
  val command: Command,
) {
  private val parsedTitle = parseCodeLensTitle(title)

  val icon: Icon? get() = parsedTitle.icon

  /** The title without its codicon tokens, which the icon of the marker shows instead. */
  val text: @NlsSafe String get() = parsedTitle.text
}

/** The lenses of one document line, anchored at the first one. */
private class GutterLensLine(val anchor: PsiElement, val anchorRange: TextRange) {
  val lenses: MutableList<GutterLens> = ArrayList()

  fun createLineMarkerInfo(file: VirtualFile): LineMarkerInfo<PsiElement> {
    val icon = lenses.firstNotNullOfOrNull { it.icon } ?: AllIcons.Actions.Execute
    val tooltip = lenses.joinToString(separator = "<br>") { StringUtil.escapeXmlEntities(it.text) }
    return LineMarkerInfo(
      anchor,
      anchorRange,
      icon,
      { tooltip },
      LspCodeLensGutterNavigationHandler(file, lenses.toList()),
      GutterIconRenderer.Alignment.LEFT,
      { tooltip },
    )
  }
}

/**
 * Runs the lens of the gutter icon, and offers the choice when the line carries more than one.
 *
 * The click goes through [LspCodeLensSupport.codeLensClicked], the same entry point as a Code Vision click, which runs on the
 * EDT and sends `workspace/executeCommand` unless the integration handles the command itself.
 */
private class LspCodeLensGutterNavigationHandler(
  private val file: VirtualFile,
  private val lenses: List<GutterLens>,
) : GutterIconNavigationHandler<PsiElement> {

  override fun navigate(e: MouseEvent?, elt: PsiElement?) {
    val single = lenses.singleOrNull()
    if (single != null) {
      execute(single, e)
      return
    }
    val step = object : BaseListPopupStep<GutterLens>(popupTitle(), lenses) {
      override fun getTextFor(value: GutterLens): String = value.text

      override fun getIconFor(value: GutterLens): Icon? = value.icon

      override fun onChosen(selectedValue: GutterLens, finalChoice: Boolean): PopupStep<*>? =
        doFinalStep { execute(selectedValue, e) }
    }
    val popup = JBPopupFactory.getInstance().createListPopup(step)
    if (e == null) popup.showInFocusCenter() else popup.show(RelativePoint(e))
  }

  private fun execute(lens: GutterLens, e: MouseEvent?) {
    lens.customizer.codeLensClicked(lens.lspClient as LspClient, file, lens.command, e)
  }

  private fun popupTitle(): @NlsContexts.PopupTitle String = LspBundle.message("lineMarkers.popup.title.codeLens")
}
