package com.intellij.platform.ml.impl.correctness.autoimport

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.PairProcessor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class InspectionBasedImportFixer : ImportFixer {

  protected abstract fun getAutoImportInspections(element: PsiElement?): List<LocalInspectionTool>
  protected abstract fun filterApplicableFixes(fixes: List<LocalQuickFixOnPsiElement>): List<LocalQuickFixOnPsiElement>
  override fun runAutoImport(file: PsiFile, editor: Editor, suggestionRange: TextRange) {
    val elements = SyntaxTraverser.psiTraverser(file)
      .onRange(suggestionRange)
      .toList()
    val indicator = when (ApplicationManager.getApplication().isUnitTestMode) {
      false -> ProgressManager.getInstance().progressIndicator
      true -> DaemonProgressIndicator()
    }

    val fixes = InspectionEngine.inspectElements(
      getAutoImportInspections(file).map { LocalInspectionToolWrapper(it) },
      file,
      file.textRange,
      true,
      true,
      indicator,
      elements,
      PairProcessor.alwaysTrue()
    ).values.flatMap { problemDescriptors ->
      problemDescriptors.flatMap { it.fixes.orEmpty().toList() }
    }.filterIsInstance<LocalQuickFixOnPsiElement>()

    applyFixes(editor, filterApplicableFixes(fixes))
  }

  fun areFixableByAutoImport(problems: List<ProblemDescriptor>): Boolean {
    return problems.all {
      val fixes = it.fixes.orEmpty().filterIsInstance<LocalQuickFixOnPsiElement>()
      filterApplicableFixes(fixes).isNotEmpty()
    }
  }

  open fun applyFixes(editor: Editor, fixes: List<LocalQuickFixOnPsiElement>) {
    val fixToApply = fixes.firstOrNull() ?: return // To avoid layering of some import popups on others
    val lastModified = editor.document.modificationStamp
    fun action() = ApplicationManager.getApplication().runWriteAction {
      fixToApply.applyFix()
    }
    ApplicationManager.getApplication().invokeLater(::action, ModalityState.defaultModalityState()) {
      editor.document.modificationStamp != lastModified
    }
  }
}