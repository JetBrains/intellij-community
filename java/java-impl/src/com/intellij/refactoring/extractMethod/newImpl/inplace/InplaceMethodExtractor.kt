// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.collectors.fus.ui.GotItUsageCollector
import com.intellij.internal.statistic.collectors.fus.ui.GotItUsageCollectorGroup
import com.intellij.internal.statistic.eventLog.events.FusInputEvent
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.ExtractMethodDialog
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper
import com.intellij.refactoring.extractMethod.newImpl.ExtractSelector
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.refactoring.rename.inplace.TemplateInlayUtil
import com.intellij.refactoring.suggested.SuggestedRefactoringProvider
import com.intellij.refactoring.suggested.range
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.GotItTooltip
import com.intellij.util.SmartList
import org.jetbrains.annotations.NonNls
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.CompletableFuture

class InplaceMethodExtractor(private val editor: Editor,
                             private val context: ExtractParameters,
                             private val extractor: InplaceExtractMethodProvider,
                             private val popupProvider: ExtractMethodPopupProvider)
  : InplaceRefactoring(editor, null, context.targetClass.project) {

  companion object {
    private val INPLACE_METHOD_EXTRACTOR = Key<InplaceMethodExtractor>("InplaceMethodExtractor")

    fun getActiveExtractor(editor: Editor): InplaceMethodExtractor? {
      return TemplateManagerImpl.getTemplateState(editor)?.properties?.get(INPLACE_METHOD_EXTRACTOR) as? InplaceMethodExtractor
    }

    private fun setActiveExtractor(editor: Editor, extractor: InplaceMethodExtractor) {
      TemplateManagerImpl.getTemplateState(editor)?.properties?.put(INPLACE_METHOD_EXTRACTOR, extractor)
    }
  }

  init {
    initPopupOptionsAdvertisement()
  }

  private val caretToRevert: Int = editor.caretModel.currentCaret.offset

  private val selectionToRevert: TextRange? = ExtractMethodHelper.findEditorSelection(editor)

  private val textToRevert: String = editor.document.text

  private lateinit var methodNameRange: RangeMarker

  private lateinit var methodCallExpressionRange: RangeMarker

  private val disposable = Disposer.newDisposable()

  fun prepareCodeForTemplate() {
    val project = myProject
    val document = editor.document

    val rangeToExtract = document.createGreedyRangeMarker(context.range)

    val (method, callExpression) = extractMethod(extractor, context)

    val highlighting = createInsertedHighlighting(editor, method.textRange)
    Disposer.register(disposable, highlighting)

    methodCallExpressionRange = document.createGreedyRangeMarker(callExpression.methodExpression.textRange)
    Disposer.register(disposable, { methodCallExpressionRange.dispose() })
    methodNameRange = document.createGreedyRangeMarker(method.nameIdentifier!!.textRange)
    Disposer.register(disposable, { methodNameRange.dispose() })
    editor.caretModel.moveToOffset(methodCallExpressionRange.range!!.startOffset)
    setElementToRename(method)

    val preview = EditorCodePreview.create(editor)
    Disposer.register(disposable, preview)

    val callLines = findLines(document, rangeToExtract.range!!)
    val file = method.containingFile.virtualFile
    preview.addPreview(callLines) { navigate(project, file, methodCallExpressionRange.endOffset)}

    val methodLines = findLines(document, method.textRange).trimToLength(4)
    preview.addPreview(methodLines) { navigate(project, file, methodNameRange.endOffset) }
  }

  fun createInsertedHighlighting(editor: Editor, range: TextRange): Disposable {
    val project = editor.project ?: return Disposable {}
    val highlighters = SmartList<RangeHighlighter>()
    val manager = HighlightManager.getInstance(project)
    manager.addOccurrenceHighlight(editor, range.startOffset, range.endOffset, DiffColors.DIFF_INSERTED, 0, highlighters)
    return Disposable {
      highlighters.forEach { highlighter -> manager.removeSegmentHighlighter(editor, highlighter) }
    }
  }

  fun extractMethod(extractor: InplaceExtractMethodProvider, parameters: ExtractParameters): Pair<PsiMethod, PsiMethodCallExpression> {
    val project = parameters.targetClass.project
    val file = parameters.targetClass.containingFile
    val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: throw IllegalStateException()

    val elements = ExtractSelector().suggestElementsToExtract(parameters.targetClass.containingFile, parameters.range)
    MethodExtractor.sendRefactoringStartedEvent(elements.toTypedArray())
    val (method, call) = extractor.extract(parameters.targetClass, elements, parameters.methodName, parameters.static)
    val methodPointer = SmartPointerManager.createPointer(method)
    val callPointer = SmartPointerManager.createPointer(call)
    val manager = PsiDocumentManager.getInstance(project)
    manager.doPostponedOperationsAndUnblockDocument(document)
    manager.commitDocument(document)
    return Pair(methodPointer.element!!, callPointer.element!!)
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

  private fun installGotItTooltips(){
    val parentDisposable = Disposer.newDisposable().also { EditorUtil.disposeWithEditor(editor, it) }
    val previousBalloonFuture = createNavigationGotIt(parentDisposable)?.showInEditor(editor, methodCallExpressionRange.range!!)
    val nameRange = methodNameRange.range ?: return
    val disposable = createChangeBasedDisposable(editor)
    val caretListener = object: CaretListener {
      override fun caretPositionChanged(event: CaretEvent) {
        if (editor.logicalPositionToOffset(event.newPosition) in nameRange) {
          previousBalloonFuture?.thenAccept { balloon -> balloon.hide(true) }
          createChangeSignatureGotIt(parentDisposable)?.showInEditor(editor, nameRange)
          Disposer.dispose(disposable)
        }
      }
    }
    editor.caretModel.addCaretListener(caretListener, disposable)
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

    withMaxWidth(250)
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

  override fun performInplaceRefactoring(nameSuggestions: LinkedHashSet<String>?): Boolean {
    try {
      ApplicationManager.getApplication().runWriteAction { prepareCodeForTemplate() }
      val succeed = super.performInplaceRefactoring(nameSuggestions)
      if (!succeed) {
        Disposer.dispose(disposable)
      }
      return succeed
    } catch (e: Throwable) {
      Disposer.dispose(disposable)
      throw e
    }
  }

  override fun checkLocalScope(): PsiElement {
    return context.targetClass
  }

  override fun revertState() {
    super.revertState()
    WriteCommandAction.runWriteCommandAction(myProject) {
      editor.document.setText(textToRevert)
      PsiDocumentManager.getInstance(myProject).commitDocument(editor.document)
    }
    editor.caretModel.moveToOffset(caretToRevert)
    if (selectionToRevert != null) {
      editor.selectionModel.setSelection(selectionToRevert.startOffset, selectionToRevert.endOffset)
    }
  }

  override fun afterTemplateStart() {
    super.afterTemplateStart()
    popupProvider.setChangeListener { restartInplace() }
    popupProvider.setShowDialogAction { actionEvent -> restartInDialog(actionEvent == null) }
    val templateState = TemplateManagerImpl.getTemplateState(myEditor) ?: return
    Disposer.register(templateState, disposable)
    val editor = templateState.editor as? EditorImpl ?: return
    val presentation = TemplateInlayUtil.createSettingsPresentation(editor) { onClickEvent -> logStatisticsOnShow(editor, onClickEvent) }
    val templateElement = object : TemplateInlayUtil.SelectableTemplateElement(presentation) {
      override fun onSelect(templateState: TemplateState) {
        super.onSelect(templateState)
        logStatisticsOnShow(editor)
      }
    }
    val offset = templateState.currentVariableRange?.endOffset ?: return
    TemplateInlayUtil.createNavigatableButtonWithPopup(templateState, offset, presentation, popupProvider.panel,
                                                                      templateElement) { logStatisticsOnHide(popupProvider) }
    setActiveExtractor(editor, this)

    Disposer.register(templateState, { SuggestedRefactoringProvider.getInstance(myProject).reset() })

    templateState.addTemplateStateListener(object: TemplateEditingAdapter() {
      override fun templateFinished(template: Template, brokenOff: Boolean) {
        afterTemplateFinished(brokenOff)
      }
    })
    installMethodNameValidation(templateState)
  }

  private fun afterTemplateFinished(brokenOff: Boolean) {
    if (! brokenOff && validationPassed){
      InplaceExtractMethodCollector.executed.log(context.methodName != getMethodName())
      installGotItTooltips()
      PsiDocumentManager.getInstance(myProject).commitAllDocuments()
      val extractedMethod = findExtractedMethod()
      if (extractedMethod != null) {
        MethodExtractor.sendRefactoringDoneEvent(extractedMethod)
        extractor.postprocess(editor, extractedMethod)
      }
    }
  }

  private fun findExtractedMethod(): PsiMethod? {
    val file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.document) ?: return null
    return PsiTreeUtil.findElementOfClassAtOffset(file, methodNameRange.startOffset, PsiMethod::class.java, false)
  }

  private fun logStatisticsOnShow(editor: Editor, mouseEvent: MouseEvent? = null){
    val showEvent = mouseEvent
                    ?: KeyEvent(editor.component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_TAB, KeyEvent.VK_TAB.toChar())
    InplaceExtractMethodCollector.show.log(editor.project, FusInputEvent(showEvent, javaClass.simpleName))
  }

  private fun logStatisticsOnHide(popupProvider: ExtractMethodPopupProvider){
    InplaceExtractMethodCollector.hide.log(myProject, popupProvider.isChanged)
    if (popupProvider.annotate != popupProvider.annotateDefault) {
      val change = if (popupProvider.annotate == true) ExtractMethodSettingChange.AnnotateOn else ExtractMethodSettingChange.AnnotateOff
      logSettingsChange(change)
    }
    if (popupProvider.makeStatic != popupProvider.makeStaticDefault) {
      val change = when {
        popupProvider.makeStatic == true && popupProvider.staticPassFields -> ExtractMethodSettingChange.MakeStaticWithFieldsOn
        popupProvider.makeStatic == false && popupProvider.staticPassFields -> ExtractMethodSettingChange.MakeStaticWithFieldsOff
        popupProvider.makeStatic == true && ! popupProvider.staticPassFields -> ExtractMethodSettingChange.MakeStaticOn
        popupProvider.makeStatic == false && ! popupProvider.staticPassFields -> ExtractMethodSettingChange.MakeStaticOff
        else -> null
      }
      logSettingsChange(change)
    }
  }

  private fun logSettingsChange(settingsChange: ExtractMethodSettingChange?){
    if (settingsChange != null) {
      InplaceExtractMethodCollector.settingsChanged.log(myProject, settingsChange)
    }
  }

  private fun setMethodName(methodName: String) {
    editor.document.replaceString(methodCallExpressionRange.startOffset, methodCallExpressionRange.endOffset, methodName)
    editor.document.replaceString(methodNameRange.startOffset, methodNameRange.endOffset, methodName)
  }

  private fun getMethodName() = editor.document.getText(TextRange(methodNameRange.startOffset, methodNameRange.endOffset))

  var validationPassed = false

  private fun installMethodNameValidation(templateState: TemplateState) {
    templateState.addTemplateStateListener(object: TemplateEditingAdapter() {

      var errorMethodName: String? = null
      var errorMessage: @NonNls String? = null

      override fun beforeTemplateFinished(state: TemplateState, template: Template?) {
        val methodName = getMethodName()
        fun isValidName(): Boolean = PsiNameHelper.getInstance(myProject).isIdentifier(methodName)
        fun hasSingleResolve(): Boolean {
          val file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.document) ?: return false
          val methodCall = PsiTreeUtil.findElementOfClassAtOffset(file, methodCallExpressionRange.startOffset, PsiMethodCallExpression::class.java, true)
          return methodCall?.resolveMethod() != null
        }
        errorMessage = when {
          ! isValidName() -> JavaRefactoringBundle.message("extract.method.error.invalid.name")
          ! hasSingleResolve() -> JavaRefactoringBundle.message("extract.method.error.method.conflict")
          else -> null
        }
        if (errorMessage != null) {
          errorMethodName = getMethodName()
          performCleanup()
        } else {
          validationPassed = true
        }
      }

      override fun templateFinished(template: Template, brokenOff: Boolean) {
        if (! brokenOff) restartWithInvalidName()
      }

      override fun templateCancelled(template: Template?) {
        restartWithInvalidName()
      }

      private fun restartWithInvalidName(){
        ApplicationManager.getApplication().invokeLater {
          val message = errorMessage
          val methodName = errorMethodName
          if (message != null && methodName != null) {
            WriteCommandAction.runWriteCommandAction(myProject) {
              val extractor = InplaceMethodExtractor(editor, context.copy(methodName = "extracted"), extractor, popupProvider)
              extractor.performInplaceRefactoring(linkedSetOf())
              extractor.setMethodName(methodName)
              CommonRefactoringUtil.showErrorHint(myProject, editor, message, ExtractMethodHandler.getRefactoringName(), null)
            }
          }
        }
      }
    })
  }

  fun restartInDialog(isLinkUsed: Boolean = false) {
    InplaceExtractMethodCollector.openExtractDialog.log(myProject, isLinkUsed)
    val updatedContext = context.update(getMethodName(), popupProvider.annotate, popupProvider.makeStatic)
    performCleanup()
    val elements = ExtractSelector().suggestElementsToExtract(updatedContext.targetClass.containingFile, updatedContext.range)
    extractor.extractInDialog(updatedContext.targetClass, elements, updatedContext.methodName, updatedContext.static)
  }

  private fun ExtractParameters.update(methodName: String, annotate: Boolean?, static: Boolean?): ExtractParameters {
    if (annotate != null) {
      PropertiesComponent.getInstance(myProject).setValue(ExtractMethodDialog.EXTRACT_METHOD_GENERATE_ANNOTATIONS, annotate, true)
    }

    var context = this.copy(methodName = methodName)
    if (annotate != null) {
      context = context.copy(annotate = annotate)
    }
    if (static != null) {
      context = context.copy(static = static)
    }
    return context
  }

  private fun restartInplace() {
    val updatedContext = context.update(getMethodName(), popupProvider.annotate, popupProvider.makeStatic)
    performCleanup()
    WriteCommandAction.runWriteCommandAction(myProject) {
      InplaceMethodExtractor(editor, updatedContext, extractor, popupProvider).performInplaceRefactoring(linkedSetOf())
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

  private fun Document.createGreedyRangeMarker(range: TextRange): RangeMarker {
    return createRangeMarker(range).also {
      it.isGreedyToLeft = true
      it.isGreedyToRight = true
    }
  }

  private fun IntRange.trimToLength(maxLength: Int) = first until first + minOf(maxLength, last - first + 1)

  private fun navigate(project: Project, file: VirtualFile, offset: Int) {
    val descriptor = OpenFileDescriptor(project, file, offset)
    descriptor.navigate(true)
    descriptor.dispose()
  }

  private fun findLines(document: Document, range: TextRange): IntRange {
    return document.getLineNumber(range.startOffset)..document.getLineNumber(range.endOffset)
  }
}