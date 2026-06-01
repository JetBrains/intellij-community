package com.intellij.platform.lsp.impl.features.navigation

import com.intellij.model.Pointer
import com.intellij.navigation.NavigatableSymbol
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.platform.lsp.util.getRangeInDocument
import com.intellij.psi.PsiManager
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import kotlin.math.min

internal class LspNavigatableSymbol(
  private val targetFileOrDir: VirtualFile,
  private val targetSelectionRange: Range?,
) : NavigatableSymbol, DocumentationTarget {
  override fun createPointer(): Pointer<LspNavigatableSymbol> = Pointer {
    if (targetFileOrDir.isValid) LspNavigatableSymbol(targetFileOrDir, targetSelectionRange) else null
  }

  override fun computePresentation(): TargetPresentation = computeTargetPresentation(targetFileOrDir, targetSelectionRange)

  override fun computeDocumentationHint(): @NlsContexts.HintText String? = targetFileOrDir.path

  override fun getNavigationTargets(project: Project): List<NavigationTarget> = listOf(
    LspNavigationTarget(project, targetFileOrDir, targetSelectionRange)
  )
}


private class LspNavigationTarget(
  private val project: Project,
  private val targetFileOrDir: VirtualFile,
  private val targetSelectionRange: Range?,
) : NavigationTarget {
  override fun createPointer(): Pointer<LspNavigationTarget> = Pointer.hardPointer(this)

  override fun computePresentation(): TargetPresentation = computeTargetPresentation(targetFileOrDir, targetSelectionRange)

  override fun navigationRequest(): NavigationRequest? {
    if (targetFileOrDir.isDirectory) {
      val psiDirectory = PsiManager.getInstance(project).findDirectory(targetFileOrDir) ?: return null
      return NavigationRequest.directoryNavigationRequest(psiDirectory)
    }

    val document = FileDocumentManager.getInstance().getDocument(targetFileOrDir) ?: return null
    val offset = targetSelectionRange?.let { getOffsetInDocument(document, it.start) } ?: 0
    return NavigationRequest.sourceNavigationRequest(project, targetFileOrDir, offset)
  }
}


private fun computeTargetPresentation(targetFile: VirtualFile, targetSelectionRange: Range?): TargetPresentation =
  TargetPresentation.builder(getTargetPresentableText(targetFile, targetSelectionRange))
    .locationText(getLocationText(targetFile, targetSelectionRange?.start))
    .presentation()

private fun getTargetPresentableText(targetFileOrDir: VirtualFile, targetSelectionRange: Range?): @NlsSafe String {
  if (targetSelectionRange == null) return targetFileOrDir.name
  val document = FileDocumentManager.getInstance().getDocument(targetFileOrDir) ?: return targetFileOrDir.name
  val textRange = getRangeInDocument(document, targetSelectionRange) ?: return targetFileOrDir.name
  if (textRange.length > 0) return StringUtil.shortenTextWithEllipsis(document.getText(textRange), TARGET_PRESENTABLE_TEXT_MAX_LENGTH, 0)
  if (document.textLength <= textRange.startOffset) return targetFileOrDir.name // end of file
  val endOffset = min(document.textLength, textRange.startOffset + TARGET_PRESENTABLE_TEXT_MAX_LENGTH)
  return document.getText(TextRange(textRange.startOffset, endOffset)) + StringUtil.ELLIPSIS
}

private fun getLocationText(fileOrDir: VirtualFile, position: Position?): @NlsSafe String? =
  if (position == null || fileOrDir.isDirectory) fileOrDir.name else "${fileOrDir.name}:${position.line + 1}:${position.character + 1}"


private const val TARGET_PRESENTABLE_TEXT_MAX_LENGTH = 20
