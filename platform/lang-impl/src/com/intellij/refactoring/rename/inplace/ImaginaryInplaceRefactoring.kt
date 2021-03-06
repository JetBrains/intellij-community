// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.inplace

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.lang.Language
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.BalloonBuilder
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.inplace.ImaginaryInplaceRefactoring.startsOnTheSameElements
import com.intellij.util.ObjectUtils
import javax.swing.JComponent

/**
 * This object exists for the only sake of returning non-null value from [InplaceRefactoring.getActiveInplaceRenamer].
 * Methods (except [startsOnTheSameElements]) throw an exception to avoid calling them unintentionally.
 */
internal object ImaginaryInplaceRefactoring : InplaceRefactoring(
  ObjectUtils.sentinel("imaginary editor", Editor::class.java),
  null,
  ObjectUtils.sentinel("imaginary project", Project::class.java)
) {

  override fun startsOnTheSameElements(editor: Editor?, handler: RefactoringActionHandler?, element: Array<out PsiElement>?): Boolean {
    return false
  }

  override fun setAdvertisementText(advertisementText: String?): Unit = error("must not be called")
  override fun performInplaceRefactoring(nameSuggestions: LinkedHashSet<String>?): Boolean = error("must not be called")
  override fun notSameFile(file: VirtualFile?, containingFile: PsiFile): Boolean = error("must not be called")
  override fun getReferencesSearchScope(file: VirtualFile?): SearchScope = error("must not be called")
  override fun checkLocalScope(): PsiElement = error("must not be called")
  override fun collectAdditionalElementsToRename(stringUsages: MutableList<Pair<PsiElement, TextRange>>): Unit = error("must not be called")
  override fun createLookupExpression(selectedElement: PsiElement?): MyLookupExpression = error("must not be called")
  override fun createTemplateExpression(selectedElement: PsiElement?): Expression = error("must not be called")
  override fun acceptReference(reference: PsiReference?): Boolean = error("must not be called")
  override fun collectRefs(referencesSearchScope: SearchScope?): MutableCollection<PsiReference> = error("must not be called")
  override fun shouldStopAtLookupExpression(expression: Expression?): Boolean = error("must not be called")
  override fun isReferenceAtCaret(selectedElement: PsiElement?, ref: PsiReference?): Boolean = error("must not be called")
  override fun beforeTemplateStart(): Unit = error("must not be called")
  override fun afterTemplateStart(): Unit = error("must not be called")
  override fun restoreSelection(): Unit = error("must not be called")
  override fun restoreCaretOffset(offset: Int): Int = error("must not be called")
  override fun stopIntroduce(editor: Editor?): Unit = error("must not be called")
  override fun navigateToAlreadyStarted(oldDocument: Document?, exitCode: Int): Unit = error("must not be called")
  override fun getNameIdentifier(): PsiElement = error("must not be called")
  override fun startRename(): StartMarkAction = error("must not be called")
  override fun getVariable(): PsiNamedElement = error("must not be called")
  override fun moveOffsetAfter(success: Boolean): Unit = error("must not be called")
  override fun addAdditionalVariables(builder: TemplateBuilderImpl?): Unit = error("must not be called")
  override fun addReferenceAtCaret(refs: MutableCollection<PsiReference>?): Unit = error("must not be called")
  override fun showDialogAdvertisement(actionId: String?): Unit = error("must not be called")
  override fun getInitialName(): String = error("must not be called")
  override fun revertState(): Unit = error("must not be called")
  override fun finish(success: Boolean): Unit = error("must not be called")
  override fun performCleanup(): Unit = error("must not be called")
  override fun getRangeToRename(element: PsiElement): TextRange = error("must not be called")
  override fun getRangeToRename(reference: PsiReference): TextRange = error("must not be called")
  override fun setElementToRename(elementToRename: PsiNamedElement?): Unit = error("must not be called")
  override fun isIdentifier(newName: String?, language: Language?): Boolean = error("must not be called")
  override fun isRestart(): Boolean = error("must not be called")
  override fun startsOnTheSameElement(handler: RefactoringActionHandler?, element: PsiElement?): Boolean = error("must not be called")
  override fun releaseResources(): Unit = error("must not be called")
  override fun getComponent(): JComponent = error("must not be called")
  override fun showBalloon(): Unit = error("must not be called")
  override fun showBalloonInEditor(): Unit = error("must not be called")
  override fun adjustBalloon(builder: BalloonBuilder?): Unit = error("must not be called")
  override fun releaseIfNotRestart(): Unit = error("must not be called")
  override fun shouldSelectAll(): Boolean = error("must not be called")
  override fun getCommandName(): String = error("must not be called")
  override fun performRefactoring(): Boolean = error("must not be called")

  override fun getSelectedInEditorElement(nameIdentifier: PsiElement?,
                                          refs: MutableCollection<out PsiReference>?,
                                          stringUsages: MutableCollection<out Pair<PsiElement, TextRange>>?,
                                          offset: Int): PsiElement = error("must not be called")

  override fun addHighlights(ranges: MutableMap<TextRange, TextAttributes>,
                             editor: Editor,
                             highlighters: MutableCollection<RangeHighlighter>,
                             highlightManager: HighlightManager): Unit = error("must not be called")

  override fun buildTemplateAndStart(refs: MutableCollection<PsiReference>?,
                                     stringUsages: MutableCollection<Pair<PsiElement, TextRange>>?,
                                     scope: PsiElement?,
                                     containingFile: PsiFile?): Boolean = error("must not be called")
}
