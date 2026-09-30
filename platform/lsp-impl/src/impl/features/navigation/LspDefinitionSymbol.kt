package com.intellij.platform.lsp.impl.features.navigation

import com.intellij.model.Pointer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.platform.lsp.util.getRangeInDocument
import com.intellij.util.IconUtil
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import kotlin.math.min

/**
 * A declaration reported by
 * [textDocument/definition](https://microsoft.github.io/language-server-protocol/specification/#textDocument_definition) or
 * [textDocument/typeDefinition](https://microsoft.github.io/language-server-protocol/specification/#textDocument_typeDefinition).
 *
 * @param targetSelectionRange the range to put the caret at, `null` to navigate to the start of the file
 */
internal class LspDefinitionSymbol(
  private val project: Project,
  private val targetFile: VirtualFile,
  private val targetSelectionRange: Range?,
) : LspNavigationSymbol {
  override fun createPointer(): Pointer<LspDefinitionSymbol> = Pointer {
    if (targetFile.isValid) LspDefinitionSymbol(project, targetFile, targetSelectionRange) else null
  }

  override fun computePresentation(): TargetPresentation = computeDefinitionPresentation(project, targetFile, targetSelectionRange)

  override fun getNavigationTargets(project: Project): List<NavigationTarget> = listOf(
    LspDefinitionNavigationTarget(project, targetFile, targetSelectionRange)
  )
}


private class LspDefinitionNavigationTarget(
  private val project: Project,
  private val targetFile: VirtualFile,
  private val targetSelectionRange: Range?,
) : NavigationTarget {
  override fun createPointer(): Pointer<LspDefinitionNavigationTarget> = Pointer.hardPointer(this)

  override fun computePresentation(): TargetPresentation = computeDefinitionPresentation(project, targetFile, targetSelectionRange)

  override fun navigationRequest(): NavigationRequest? {
    val document = FileDocumentManager.getInstance().getDocument(targetFile) ?: return null
    val offset = targetSelectionRange?.let { getOffsetInDocument(document, it.start) } ?: 0
    return NavigationRequest.sourceNavigationRequest(project, targetFile, offset)
  }
}


private fun computeDefinitionPresentation(project: Project, targetFile: VirtualFile, targetSelectionRange: Range?): TargetPresentation =
  TargetPresentation.builder(getTargetPresentableText(targetFile, targetSelectionRange))
    .icon(IconUtil.computeFileIcon(targetFile, 0, project))
    .locationText(getLocationText(targetFile, targetSelectionRange?.start))
    .presentation()

private fun getTargetPresentableText(targetFile: VirtualFile, targetSelectionRange: Range?): @NlsSafe String {
  if (targetSelectionRange == null) return targetFile.name
  val document = FileDocumentManager.getInstance().getDocument(targetFile) ?: return targetFile.name
  val textRange = getRangeInDocument(document, targetSelectionRange) ?: return targetFile.name
  if (textRange.length > 0) return StringUtil.shortenTextWithEllipsis(document.getText(textRange), TARGET_PRESENTABLE_TEXT_MAX_LENGTH, 0)
  if (document.textLength <= textRange.startOffset) return targetFile.name // end of file
  val endOffset = min(document.textLength, textRange.startOffset + TARGET_PRESENTABLE_TEXT_MAX_LENGTH)
  return document.getText(TextRange(textRange.startOffset, endOffset)) + StringUtil.ELLIPSIS
}

private fun getLocationText(targetFile: VirtualFile, position: Position?): @NlsSafe String =
  if (position == null) targetFile.name else "${targetFile.name}:${position.line + 1}:${position.character + 1}"


private const val TARGET_PRESENTABLE_TEXT_MAX_LENGTH = 20
