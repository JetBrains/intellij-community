package com.intellij.platform.lsp.impl.features.extract

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.platform.lsp.api.customization.LspCodeActionsSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.features.findPendingLspClient
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler

/**
 * Supplies one Extract handler and gates it on the support of the handler's own code action kinds,
 * so a server that supports one Extract subtype does not enable the other Extract actions.
 * The [kinds] list is a preference order: the handler requests a later kind only when no earlier kind yields an action.
 * The variable and field extractions fall back to `refactor.extract.constant`,
 * because a server can serve its only scoped-value extraction under that kind.
 * In a class scope that extraction yields a field.
 * Inline resolves through [com.intellij.platform.lsp.impl.features.inline.LspInlineActionHandler] and needs no provider,
 * so an inline-only server does not enable the Extract actions.
 */
internal sealed class LspRefactoringSupportProvider(protected val kinds: List<String>) : RefactoringSupportProvider() {

  final override fun isAvailable(context: PsiElement): Boolean {
    val psiFile = context.containingFile ?: return false
    return findLspClientsForExtract(psiFile, kinds).isNotEmpty() || findPendingLspClientForExtract(psiFile) != null
  }

  internal class IntroduceVariable : LspRefactoringSupportProvider(listOf("refactor.extract.variable", "refactor.extract.constant")) {
    override fun getIntroduceVariableHandler(): RefactoringActionHandler = LspExtractRefactoringHandler(kinds)
  }

  internal class ExtractMethod : LspRefactoringSupportProvider(listOf("refactor.extract.function")) {
    override fun getExtractMethodHandler(): RefactoringActionHandler = LspExtractRefactoringHandler(kinds)
  }

  internal class IntroduceField : LspRefactoringSupportProvider(listOf("refactor.extract.field", "refactor.extract.constant")) {
    override fun getIntroduceFieldHandler(): RefactoringActionHandler = LspExtractRefactoringHandler(kinds)
  }

  internal class IntroduceConstant : LspRefactoringSupportProvider(listOf("refactor.extract.constant")) {
    override fun getIntroduceConstantHandler(): RefactoringActionHandler = LspExtractRefactoringHandler(kinds)
  }
}

internal fun findLspClientsForExtract(psiFile: PsiFile, kinds: List<String>): List<LspClientImpl> {
  val virtualFile = psiFile.virtualFile ?: return emptyList()
  return LspClientManagerImpl.getInstanceImpl(psiFile.project)
    .getClientsWithThisFileOpen(virtualFile)
    .filter { client ->
      client.optsInToExtract(psiFile) && kinds.any { client.supportsCodeActionsOfKind(virtualFile, it) }
    }
}

internal fun findPendingLspClientForExtract(psiFile: PsiFile): LspClientImpl? =
  findPendingLspClient(psiFile) { it.optsInToExtract(psiFile) }

private fun LspClientImpl.optsInToExtract(psiFile: PsiFile): Boolean {
  val customizer = descriptor.lspCustomization.codeActionsCustomizer
  return customizer is LspCodeActionsSupport && customizer.extractRefactoringSupport && customizer.shouldRunRefactoring(psiFile)
}
