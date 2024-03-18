package com.intellij.platform.ml.impl.correctness.autoimport

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
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
  protected abstract fun filterApplicableFixes(element: PsiElement, fixes: List<LocalQuickFix>): List<LocalQuickFix>
  override fun runAutoImport(file: PsiFile, editor: Editor, suggestionRange: TextRange, context: ImportFixer.ImportContext) {
    val elements = SyntaxTraverser.psiTraverser(file)
      .onRange(suggestionRange)
      .toList()
    val indicator = when (ApplicationManager.getApplication().isUnitTestMode) {
      false -> ProgressManager.getInstance().progressIndicator
      true -> DaemonProgressIndicator()
    }

    val problemDescriptors = InspectionEngine.inspectElements(
      getAutoImportInspections(file).map { LocalInspectionToolWrapper(it) },
      file,
      file.textRange,
      true,
      true,
      indicator,
      elements,
      PairProcessor.alwaysTrue()
    ).values.flatten()
    applyFixes(editor, problemDescriptors)
  }

  fun areFixableByAutoImport(problems: List<ProblemDescriptor>): Boolean {
    return problems.all {
      val fixes = it.fixes.orEmpty().filterIsInstance<LocalQuickFix>()
      filterApplicableFixes(it.psiElement, fixes).isNotEmpty()
    }
  }

  open fun applyFixes(editor: Editor, problemDescriptors: List<ProblemDescriptor>) {
    val (descriptor, fixToApply) = problemDescriptors
                                     .map { it to filterApplicableFixes(it.psiElement, it.fixes.orEmpty().filterIsInstance<LocalQuickFix>()) }
                                     .map { (descriptor, fixes) -> descriptor to fixes.firstOrNull() }
                                     .firstOrNull { (_, fix) -> fix != null } ?: return // To avoid layering of some import popups on others
    if (fixToApply == null) return
    val lastModified = editor.document.modificationStamp
    val project = editor.project ?: return
    fun action() = ApplicationManager.getApplication().runWriteAction {
      fixToApply.applyFix(project, descriptor)
    }
    ApplicationManager.getApplication().invokeLater(::action, ModalityState.defaultModalityState()) {
      editor.document.modificationStamp != lastModified
    }
  }
}