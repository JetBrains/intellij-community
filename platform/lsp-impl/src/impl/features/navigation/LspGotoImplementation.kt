// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.navigation

import com.intellij.codeInsight.navigation.actions.GotoImplementationAction
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.api.customization.LspGoToImplementationSupport
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.pom.PomDeclarationSearcher
import com.intellij.pom.PomNamedTarget
import com.intellij.pom.PomTarget
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.util.Consumer
import com.intellij.util.Processor

/**
 * The first half of the [Go To Implementation][GotoImplementationAction] feature for LSP-backed files.
 *
 * `GotoImplementationHandler` needs a non-null source element from `TargetElementUtil.findTargetElement()`.
 * LSP-backed files usually have flat PSI without named elements or references, so the standard lookup finds nothing.
 * This searcher runs inside `TargetElementUtilBase.getNamedElement()` and provides the source element:
 * a [LspGotoImplementationTarget] that keeps the file and the caret offset.
 * [LspDefinitionsScopedSearcher] then recognizes this target and asks the LSP server for the implementations.
 *
 * A `PomDeclarationSearcher` is used instead of a `TargetElementEvaluatorEx2` on purpose:
 * all `PomDeclarationSearcher` extensions are iterated,
 * while only one `TargetElementEvaluatorEx2` per language is ever consulted (see IJPL-189562).
 */
internal class LspImplementationDeclarationSearcher : PomDeclarationSearcher() {
  override fun findDeclarationsAt(element: PsiElement, offsetInElement: Int, consumer: Consumer<in PomTarget>) {
    // This function is called for many features that need a named element at the offset.
    // We care only about the "Go To Implementation" action.
    val actionClass = service<CurrentActionHolder>().currentActionClass ?: return
    if (!GotoImplementationAction::class.java.isAssignableFrom(actionClass)) return

    val psiFile = element.containingFile ?: return
    val project = psiFile.project
    if (project.isDefault) return
    val file = psiFile.virtualFile ?: return
    if (file is VirtualFileWindow) return

    val hasSupportingClient = LspClientManagerImpl.getInstanceImpl(project).getClientsForFileRequests(file).any {
      it.supportsGotoImplementation(file) &&
      it.descriptor.lspCustomization.goToImplementationCustomizer is LspGoToImplementationSupport
    }
    if (!hasSupportingClient) return

    consumer.consume(LspGotoImplementationTarget(psiFile, element.textRange.startOffset + offsetInElement))
  }
}


/**
 * The source element for the "Go To Implementation" action in an LSP-backed file.
 * Keeps the caret offset because `DefinitionsScopedSearch.SearchParameters` transfers only an element.
 *
 * The target must not implement `PsiTarget`.
 * For a `PsiTarget`, the platform `PomDefinitionSearch` reports the navigation element as an implementation.
 * That self result would win over the LSP results.
 */
internal class LspGotoImplementationTarget(val psiFile: PsiFile, val offsetInFile: Int) : PomNamedTarget {
  /**
   * The word at the caret. The "Implementations of ..." popup title shows it.
   */
  override fun getName(): String = wordAt(psiFile, offsetInFile) ?: psiFile.name

  override fun isValid(): Boolean = psiFile.isValid

  override fun navigate(requestFocus: Boolean) {
    val virtualFile = psiFile.virtualFile ?: return
    PsiNavigationSupport.getInstance().createNavigatable(psiFile.project, virtualFile, offsetInFile).navigate(requestFocus)
  }

  override fun canNavigate(): Boolean = psiFile.virtualFile != null

  override fun canNavigateToSource(): Boolean = canNavigate()

  override fun equals(other: Any?): Boolean =
    other is LspGotoImplementationTarget && other.psiFile == psiFile && other.offsetInFile == offsetInFile

  override fun hashCode(): Int = 31 * psiFile.hashCode() + offsetInFile
}


/**
 * An implementation found by the LSP server.
 * A wrapper is needed because LSP-backed files usually have flat PSI:
 * `psiFile.findElementAt()` returns the leaf that spans the whole file,
 * so navigation to that leaf would put the caret at the file start.
 * This element keeps the exact target offset, and navigation goes to it via [getTextOffset].
 */
internal class LspImplementationTargetElement(private val targetPsiFile: PsiFile, private val targetOffset: Int) : FakePsiElement() {
  override fun getParent(): PsiElement = targetPsiFile
  override fun getContainingFile(): PsiFile = targetPsiFile
  override fun getTextOffset(): Int = targetOffset
  override fun getTextRange(): TextRange = TextRange.from(targetOffset, 0)
  override fun isValid(): Boolean = targetPsiFile.isValid
  override fun getName(): String = wordAt(targetPsiFile, targetOffset) ?: targetPsiFile.name
  override fun getLocationString(): String = targetPsiFile.name

  override fun equals(other: Any?): Boolean =
    other is LspImplementationTargetElement && other.targetPsiFile == targetPsiFile && other.targetOffset == targetOffset

  override fun hashCode(): Int = 31 * targetPsiFile.hashCode() + targetOffset
}


/**
 * Returns the word around the offset, or null when the offset does not touch a word.
 */
private fun wordAt(psiFile: PsiFile, offsetInFile: Int): String? {
  val text = psiFile.virtualFile
               ?.let { FileDocumentManager.getInstance().getCachedDocument(it) }
               ?.charsSequence
             ?: return null
  var start = offsetInFile.coerceIn(0, text.length)
  var end = start
  while (start > 0 && Character.isJavaIdentifierPart(text[start - 1])) start--
  while (end < text.length && Character.isJavaIdentifierPart(text[end])) end++
  return if (start < end) text.subSequence(start, end).toString() else null
}


/**
 * The second half of the [Go To Implementation][GotoImplementationAction] feature for LSP-backed files.
 *
 * Recognizes the [LspGotoImplementationTarget] source element that [LspImplementationDeclarationSearcher] provided.
 * Sends the
 * [textDocument/implementation](https://microsoft.github.io/language-server-protocol/specification/#textDocument_implementation)
 * request and converts the response to PSI elements in the target files.
 */
internal class LspDefinitionsScopedSearcher : QueryExecutorBase<PsiElement, DefinitionsScopedSearch.SearchParameters>(true) {
  override fun processQuery(queryParameters: DefinitionsScopedSearch.SearchParameters, consumer: Processor<in PsiElement>) {
    val sourceElement = queryParameters.element as? PomTargetPsiElement ?: return
    val target = sourceElement.target as? LspGotoImplementationTarget ?: return
    val file = target.psiFile.virtualFile ?: return
    val project = queryParameters.project
    val psiManager = PsiManager.getInstance(project)

    for (lspClient in LspClientManagerImpl.getInstanceImpl(project).getClientsForFileRequests(file)) {
      if (!lspClient.supportsGotoImplementation(file)) continue
      if (lspClient.descriptor.lspCustomization.goToImplementationCustomizer !is LspGoToImplementationSupport) continue

      val locationLinks = lspClient.requestExecutor.getImplementations(file, target.offsetInFile)
      for (locationLink in locationLinks) {
        val targetFile = lspClient.libraryFiles.findTargetFile(locationLink.targetUri) ?: continue
        val targetPsiFile = psiManager.findFile(targetFile) ?: continue
        val range = locationLink.targetSelectionRange ?: locationLink.targetRange
        val targetElement = range
                              ?.let { FileDocumentManager.getInstance().getDocument(targetFile) }
                              ?.let { getOffsetInDocument(it, range.start) }
                              ?.let { LspImplementationTargetElement(targetPsiFile, it) }
                            ?: targetPsiFile
        if (!consumer.process(targetElement)) return
      }
    }
  }
}
