package com.intellij.platform.lsp.impl.features.usages

import com.intellij.find.actions.FindUsagesAction.SEARCH_TARGETS
import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.injected.editor.EditorWindow
import com.intellij.model.Pointer
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.customization.LspFindReferencesSupport
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.util.getLsp4jPosition
import com.intellij.util.IconUtil
import org.eclipse.lsp4j.Position
import org.jetbrains.annotations.NonNls

/**
 * Helps to implement the 'Find Usages' and 'Show Usages' features backed by an LSP server
 * ([textDocument/references](https://microsoft.github.io/language-server-protocol/specification/#textDocument_references) request).
 *
 * The approach to implement the `search.targets` rule is chosen according to the
 * [FindUsagesAction.update][com.intellij.find.actions.FindUsagesAction.update] function implementation, which effectively delegates to
 * [SearchTargetVariantsDataRule:targetVariants][com.intellij.find.actions.targetVariants].
 * The [TargetVariant][com.intellij.find.actions.TargetVariant] is a sealed class with three subclasses, but only
 * [SearchTargetVariant][com.intellij.find.actions.SearchTargetVariant] subclass works for LSP-based usages search:
 * - [PsiTargetVariant][com.intellij.find.actions.PsiTargetVariant] doesn't work because there may be no PSI at all in the files
 * that are supported via an LSP server
 * - [CustomTargetVariant][com.intellij.find.actions.CustomTargetVariant] doesn't work great
 * because its [handle][com.intellij.find.actions.CustomTargetVariant.handle] function
 * doesn't use [UsageVariantHandler][com.intellij.find.actions.UsageVariantHandler]; this results is loosing understanding
 * whether the current action is [FindUsagesAction][com.intellij.find.actions.FindUsagesAction]
 * or [ShowUsagesAction][com.intellij.find.actions.ShowUsagesAction]
 * - [SearchTargetVariant][com.intellij.find.actions.SearchTargetVariant] works great
 * as its [handle][com.intellij.find.actions.SearchTargetVariant.handle] implementation
 * calls [UsageVariantHandler.handleTarget][com.intellij.find.actions.UsageVariantHandler.handleTarget]
 *
 * @see LspReferencesQuery
 */
internal class LspSearchTargetsRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    sink.lazyValue(SEARCH_TARGETS) {
      searchTargets(it)
    }
  }

  private fun searchTargets(dataProvider: DataMap): Collection<SearchTarget>? {
    val editor = dataProvider[CommonDataKeys.EDITOR]?.takeUnless { it is EditorWindow } ?: return null
    val project = editor.project?.takeUnless { it.isDefault } ?: return null
    val document = editor.document
    val file = FileDocumentManager.getInstance().getFile(document) ?: return null
    val offset = editor.caretModel.offset
    val position = getLsp4jPosition(document, offset)

    val lspClients = LspClientManagerImpl.getInstanceImpl(project)
                       .getClientsWithThisFileOpen(file)
                       .filter { it.descriptor.lspCustomization.findReferencesCustomizer is LspFindReferencesSupport }
                       .filter { it.supportsFindReferences(file) }
                       .takeIf { it.isNotEmpty() }
                     ?: return null

    return listOf(LspSearchTarget(lspClients, file, position))
  }
}

internal class LspSearchTarget(val lspClients: Collection<LspClient>, val file: VirtualFile, val position: Position) : SearchTarget {
  override fun createPointer(): Pointer<out SearchTarget> = Pointer.hardPointer(this)

  // It would be great to have something more meaningful here. For example, guess a word at caret.
  // We can get the text range of the declaration from the server
  // (by passing `ReferenceContext.includeDeclaration = true` to the server).
  // But we'll get the response much later than the Platform asks for `targetPresentableText`.
  private val fileNameAndPosition: @NlsSafe String = "${file.name}:${position.line + 1}:${position.character + 1}"

  private val presentableName: @NonNls String = lspClients.singleOrNull()?.descriptor?.presentableName ?: "LSP"

  // Used to render the header in the 'Show Usages' popup
  // and also the tab header in the 'Find Usages' tool window
  override val usageHandler: UsageHandler =
    UsageHandler { LspBundle.message("0.find.references.1", presentableName, fileNameAndPosition) }

  // Used to render the search target in the result tree in the 'Find Usages' tool window
  override fun presentation(): TargetPresentation =
    TargetPresentation.builder(fileNameAndPosition)
      .icon(IconUtil.getIcon(file, Iconable.ICON_FLAG_VISIBILITY, lspClients.first().project))
      .presentation()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as LspSearchTarget

    if (file != other.file) return false
    if (position != other.position) return false

    return true
  }

  override fun hashCode(): Int {
    var result = file.hashCode()
    result = 31 * result + position.hashCode()
    return result
  }
}
