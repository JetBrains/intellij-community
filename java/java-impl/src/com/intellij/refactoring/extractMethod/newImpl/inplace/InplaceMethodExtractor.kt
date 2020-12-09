// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.injected.changesHandler.range
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.ExtractMethodDialog
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.newImpl.*
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.refactoring.rename.inplace.TemplateInlayUtil
import com.intellij.util.PsiNavigateUtil

class InplaceMethodExtractor(val editor: Editor, val extractOptions: ExtractOptions, private val popupProvider: ExtractMethodPopupProvider)
  : InplaceRefactoring(editor, null, extractOptions.project) {

  companion object {
    private val INPLACE_METHOD_EXTRACTOR = Key<InplaceMethodExtractor>("InplaceMethodExtractor")

    fun getActiveExtractor(editor: Editor): InplaceMethodExtractor? {
      return TemplateManagerImpl.getTemplateState(editor)?.properties?.get(INPLACE_METHOD_EXTRACTOR) as? InplaceMethodExtractor
    }

    private fun setActiveExtractor(editor: Editor, extractor: InplaceMethodExtractor) {
      TemplateManagerImpl.getTemplateState(editor)?.properties?.put(INPLACE_METHOD_EXTRACTOR, extractor)
    }
  }

  private val fragmentsToRevert = mutableListOf<FragmentState>()

  private val caretToRevert: Int = editor.caretModel.currentCaret.offset

  private val selectionToRevert: TextRange? = ExtractMethodHelper.findEditorSelection(editor)

  private val extractedRange = enclosingTextRangeOf(extractOptions.elements.first(), extractOptions.elements.last())

  fun prepareCodeForTemplate() {
    FileEditorManager.getInstance(myProject).selectedEditor!!
    val project = extractOptions.project
    val document = editor.document

    val elements = extractOptions.elements
    val callRange = document.createGreedyRangeMarker(enclosingTextRangeOf(elements.first(), elements.last()))
    val callText = document.getText(callRange.range)
    val replacedCall = FragmentState(callRange, callText)
    fragmentsToRevert.add(replacedCall)

    val startSibling = extractOptions.anchor.nextSibling
    val endSibling = PsiTreeUtil.skipWhitespacesForward(startSibling) ?: startSibling
    val methodRange = document.createGreedyRangeMarker(enclosingTextRangeOf(startSibling, endSibling))
    val methodText = document.getText(methodRange.range)
    val replacedMethod = FragmentState(methodRange, methodText)
    fragmentsToRevert.add(replacedMethod)

    val javaFile = extractOptions.anchor.containingFile as PsiJavaFile
    val importRange = document.createGreedyRangeMarker(javaFile.importList?.textRange ?: TextRange(0, 0))
    val replacedImport = FragmentState(importRange, document.getText(importRange.range))
    fragmentsToRevert.add(replacedImport)

    val (callElements, method) = MethodExtractor().extractMethod(extractOptions)
    val callExpression = PsiTreeUtil.findChildOfType(callElements.first(), PsiMethodCallExpression::class.java, false)!!
    editor.caretModel.moveToOffset(callExpression.textOffset)
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
    setElementToRename(method)
  }

  override fun performInplaceRefactoring(nameSuggestions: LinkedHashSet<String>?): Boolean {
    ApplicationManager.getApplication().runWriteAction { prepareCodeForTemplate() }
    return super.performInplaceRefactoring(nameSuggestions)
  }

  override fun revertState() {
    super.revertState()
    WriteCommandAction.runWriteCommandAction(myProject) {
      fragmentsToRevert.forEach { fragment ->
        editor.document.replaceString(fragment.range.startOffset, fragment.range.endOffset, fragment.text)
      }
      PsiDocumentManager.getInstance(myProject).commitDocument(editor.document)
      editor.caretModel.moveToOffset(caretToRevert)
      if (selectionToRevert != null) {
        editor.selectionModel.setSelection(selectionToRevert.startOffset, selectionToRevert.endOffset)
      }
    }
  }

  override fun afterTemplateStart() {
    super.afterTemplateStart()
    popupProvider.setChangeListener { restartInplace() }
    popupProvider.setShowDialogAction { restartInDialog() }
    popupProvider.setNavigateMethodAction {
      val template = TemplateManagerImpl.getTemplateState(editor)
      if (template != null) {
        IdeEventQueue.getInstance().popupManager.closeAllPopups()
        template.gotoEnd(false)
        PsiNavigateUtil.navigate(myElementToRename.navigationElement)
      }
    }
    val templateState = TemplateManagerImpl.getTemplateState(myEditor) ?: return
    val editor = templateState.editor as? EditorImpl ?: return
    val presentation = TemplateInlayUtil.createSettingsPresentation(editor)
    val offset = templateState.currentVariableRange?.endOffset ?: return
    TemplateInlayUtil.createNavigatableButtonWithPopup(templateState, offset, presentation, popupProvider.panel) ?: return
    fragmentsToRevert.forEach { Disposer.register(templateState, it) }
    setActiveExtractor(editor, this)
  }

  fun restartInDialog() {
    val newOptions = revertAndMapOptions(popupProvider.annotate, popupProvider.makeStatic)
    MethodExtractor().doDialogExtract(newOptions)
  }

  private fun revertAndMapOptions(annotate: Boolean?, makeStatic: Boolean?): ExtractOptions {
    if (annotate != null) {
      PropertiesComponent.getInstance(extractOptions.project).setValue(ExtractMethodDialog.EXTRACT_METHOD_GENERATE_ANNOTATIONS, annotate, true)
    }

    val methodNameRange = TemplateManagerImpl.getTemplateState(editor)?.currentVariableRange ?: throw IllegalStateException()
    val methodName = editor.document.getText(methodNameRange)
    val containingClass = extractOptions.anchor.containingClass ?: throw IllegalStateException()
    performCleanup()

    val elements = ExtractSelector().suggestElementsToExtract(containingClass.containingFile, extractedRange)
    val analyzer = CodeFragmentAnalyzer(elements)
    var options = findExtractOptions(elements).copy(methodName = methodName)
    options = ExtractMethodPipeline.withTargetClass(analyzer, options, containingClass)!!
    options = if (makeStatic == true) ExtractMethodPipeline.withForcedStatic(analyzer, options)!! else options
    return options
  }

  private fun restartInplace() {
    val newOptions = revertAndMapOptions(popupProvider.annotate, popupProvider.makeStatic)
    WriteCommandAction.runWriteCommandAction(myProject) {
      InplaceMethodExtractor(editor, newOptions, popupProvider).performInplaceRefactoring(linkedSetOf())
    }
  }

  override fun performRefactoring(): Boolean {
    return false
  }

  override fun performCleanup() {
    revertState()
  }

  override fun shouldSelectAll(): Boolean = false

  override fun getCommandName(): String = ExtractMethodHandler.getRefactoringName()

  private data class FragmentState(val range: RangeMarker, val text: String) : Disposable {
    override fun dispose() {
      range.dispose()
    }
  }

  private fun Document.createGreedyRangeMarker(range: TextRange): RangeMarker {
    return createRangeMarker(range).also {
      it.isGreedyToLeft = true
      it.isGreedyToRight = true
    }
  }

  private fun enclosingTextRangeOf(start: PsiElement, end: PsiElement): TextRange = start.textRange.union(end.textRange)
}