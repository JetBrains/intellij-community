// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.hint.EditorCodePreview
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.icons.AllIcons
import com.intellij.internal.statistic.collectors.fus.ui.GotItUsageCollector
import com.intellij.internal.statistic.collectors.fus.ui.GotItUsageCollectorGroup
import com.intellij.internal.statistic.eventLog.events.FusInputEvent
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.inplace.TemplateInlayUtil
import com.intellij.refactoring.suggested.range
import com.intellij.ui.GotItTooltip
import com.intellij.util.SmartList
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.CompletableFuture

object InplaceExtractUtils {

  private fun createInsertedHighlighting(editor: Editor, range: TextRange): Disposable {
    val project = editor.project ?: return Disposable {}
    val highlighters = SmartList<RangeHighlighter>()
    val manager = HighlightManager.getInstance(project)
    manager.addOccurrenceHighlight(editor, range.startOffset, range.endOffset, DiffColors.DIFF_INSERTED, 0, highlighters)
    return Disposable {
      highlighters.forEach { highlighter -> manager.removeSegmentHighlighter(editor, highlighter) }
    }
  }

  fun createChangeBasedDisposable(editor: Editor): Disposable {
    val disposable = Disposer.newDisposable()
    EditorUtil.disposeWithEditor(editor, disposable)
    val changeListener = object: DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        Disposer.dispose(disposable)
      }
    }
    editor.document.addDocumentListener(changeListener, disposable)
    return disposable
  }

  fun createNavigationGotIt(parent: Disposable): GotItTooltip? {
    val gotoKeyboardShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_GOTO_DECLARATION)
    val gotoMouseShortcut = KeymapUtil.getFirstMouseShortcutText(IdeActions.ACTION_GOTO_DECLARATION)
    if (gotoKeyboardShortcut.isEmpty() || gotoMouseShortcut.isEmpty()) return null
    val header = JavaRefactoringBundle.message("extract.method.gotit.navigation.header")
    val message = JavaRefactoringBundle.message("extract.method.gotit.navigation.message", gotoMouseShortcut, gotoKeyboardShortcut)
    return GotItTooltip("extract.method.gotit.navigate", message, parent).withHeader(header)
  }

  fun createChangeSignatureGotIt(parent: Disposable): GotItTooltip? {
    val moveLeftShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.MOVE_ELEMENT_LEFT)
    val moveRightShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.MOVE_ELEMENT_RIGHT)
    if (moveLeftShortcut.isEmpty() || moveRightShortcut.isEmpty()) return null
    val contextActionShortcut = KeymapUtil.getFirstKeyboardShortcutText("ShowIntentionActions")
    val header = JavaRefactoringBundle.message("extract.method.gotit.signature.header")
    val message = JavaRefactoringBundle.message("extract.method.gotit.signature.message", contextActionShortcut, moveLeftShortcut, moveRightShortcut)
    return GotItTooltip("extract.method.signature.change", message, parent)
      .withIcon(AllIcons.Gutter.SuggestedRefactoringBulbDisabled)
      .withHeader(header)
  }

  fun GotItTooltip.showInEditor(editor: Editor, range: TextRange): CompletableFuture<Balloon> {
    val offset = minOf(range.startOffset + 3, range.endOffset)
    fun getPosition(): Point = editor.offsetToXY(offset)
    fun isVisible(): Boolean = editor.scrollingModel.visibleArea.contains(getPosition())
    fun updateBalloon(balloon: Balloon) {
      if (isVisible()) {
        balloon.revalidate()
      } else {
        balloon.hide(true)
        GotItUsageCollector.instance.logClose(id, GotItUsageCollectorGroup.CloseType.AncestorRemoved)
      }
    }
    withPosition(Balloon.Position.above)

    val balloonFuture = CompletableFuture<Balloon>()
    if (isVisible()) {
      setOnBalloonCreated { balloon ->
        editor.scrollingModel.addVisibleAreaListener({ updateBalloon(balloon) }, balloon)
        balloonFuture.complete(balloon)
      }
      show(editor.contentComponent, pointProvider = { _, _-> getPosition() })
    }

    return balloonFuture
  }

  fun logStatisticsOnShow(editor: Editor, mouseEvent: MouseEvent? = null){
    val showEvent = mouseEvent
                    ?: KeyEvent(editor.component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_TAB, KeyEvent.VK_TAB.toChar())
    InplaceExtractMethodCollector.show.log(editor.project, FusInputEvent(showEvent, javaClass.simpleName))
  }

  private fun logStatisticsOnHide(project: Project, popupProvider: ExtractMethodPopupProvider){
    InplaceExtractMethodCollector.hide.log(project, popupProvider.isChanged)
    if (popupProvider.annotate != popupProvider.annotateDefault) {
      val change = if (popupProvider.annotate == true) ExtractMethodSettingChange.AnnotateOn else ExtractMethodSettingChange.AnnotateOff
      logSettingsChange(project, change)
    }
    if (popupProvider.makeStatic != popupProvider.makeStaticDefault) {
      val change = when {
        popupProvider.makeStatic == true && popupProvider.staticPassFields -> ExtractMethodSettingChange.MakeStaticWithFieldsOn
        popupProvider.makeStatic == false && popupProvider.staticPassFields -> ExtractMethodSettingChange.MakeStaticWithFieldsOff
        popupProvider.makeStatic == true && ! popupProvider.staticPassFields -> ExtractMethodSettingChange.MakeStaticOn
        popupProvider.makeStatic == false && ! popupProvider.staticPassFields -> ExtractMethodSettingChange.MakeStaticOff
        else -> null
      }
      logSettingsChange(project, change)
    }
  }

  private fun logSettingsChange(project: Project, settingsChange: ExtractMethodSettingChange?){
    if (settingsChange != null) {
      InplaceExtractMethodCollector.settingsChanged.log(project, settingsChange)
    }
  }

  private fun navigateToEditorOffset(editor: Editor, offset: Int?) {
    if (offset == null) return
    val project: Project = editor.project ?: return
    val file: VirtualFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return
    val descriptor = OpenFileDescriptor(project, file, offset)
    descriptor.navigate(true)
    descriptor.dispose()
  }

  fun addInlaySettingsElement(templateState: TemplateState, settingsPopup: ExtractMethodPopupProvider): Inlay<PresentationRenderer>? {
    val editor = templateState.editor as? EditorImpl ?: return null
    val project = editor.project ?: return null
    val offset = templateState.currentVariableRange?.endOffset ?: return null
    val presentation = TemplateInlayUtil.createSettingsPresentation(editor) { onClickEvent -> logStatisticsOnShow(editor, onClickEvent) }

    val templateElement = object : TemplateInlayUtil.SelectableTemplateElement(presentation) {
      override fun onSelect(templateState: TemplateState) {
        super.onSelect(templateState)
        logStatisticsOnShow(editor)
      }
    }
    presentation.addSelectionListener { isSelected ->
      if (!isSelected) {
        logStatisticsOnHide(project, settingsPopup)
      }
    }
    return TemplateInlayUtil.createNavigatableButtonWithPopup(templateState.editor, offset, presentation, settingsPopup.panel, templateElement)
  }

  private fun addPreview(preview: EditorCodePreview, editor: Editor, lines: IntRange, navigatableOffset: Int){
    val navigatableMarker = createGreedyRangeMarker(editor.document, TextRange(navigatableOffset, navigatableOffset))
    Disposer.register(preview) { navigatableMarker.dispose() }
    preview.addPreview(lines, onClickAction = { navigateToEditorOffset(editor, navigatableMarker.range?.endOffset) })
  }

  inline fun <reified T: PsiElement> findElementAt(file: PsiFile, range: RangeMarker?): T? {
    val offset = range?.range?.startOffset ?: return null
    return PsiTreeUtil.findElementOfClassAtOffset(file, offset, T::class.java, false)
  }

  fun createGreedyRangeMarker(document: Document, range: TextRange): RangeMarker {
    return document.createRangeMarker(range).apply {
      isGreedyToLeft = true
      isGreedyToRight = true
    }
  }

  private fun IntRange.trim(maxLength: Int) = first until first + minOf(maxLength, last - first + 1)

  private fun getLinesFromTextRange(document: Document, range: TextRange): IntRange {
    return document.getLineNumber(range.startOffset)..document.getLineNumber(range.endOffset)
  }

  fun createPreview(editor: Editor, methodRange: TextRange, methodOffset: Int, callRange: TextRange, callOffset: Int): EditorCodePreview {
    val codePreview = EditorCodePreview.create(editor)
    val highlighting = createInsertedHighlighting(editor, methodRange)
    Disposer.register(codePreview, highlighting)
    addPreview(codePreview, editor, getLinesFromTextRange(editor.document, callRange), callOffset)
    addPreview(codePreview, editor, getLinesFromTextRange(editor.document, methodRange).trim(4), methodOffset)
    return codePreview
  }

}