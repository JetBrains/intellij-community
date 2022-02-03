// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.Nullability
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.IntroduceVariableUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractMethod.ExtractMethodDialog
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.addSiblingAfter
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.guessMethodName
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.wrapWithCodeBlock
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodPipeline.selectTargetClass
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodPipeline.withFilteredAnnotations
import com.intellij.refactoring.extractMethod.newImpl.MapFromDialog.mapFromDialog
import com.intellij.refactoring.extractMethod.newImpl.inplace.*
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.ConflictsUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.NonNls

class MethodExtractor {

  data class ExtractedElements(val callElements: List<PsiElement>, val method: PsiMethod)

  fun doExtract(file: PsiFile, range: TextRange) {
    val project = file.project
    val editor = PsiEditorUtil.findEditor(file) ?: return
    val activeExtractor = InplaceMethodExtractor.getActiveExtractor(editor)
    if (activeExtractor != null) {
      activeExtractor.restartInDialog()
      return
    }
    try {
      if (!CommonRefactoringUtil.checkReadOnlyStatus(file.project, file)) return
      val elements = ExtractSelector().suggestElementsToExtract(file, range)
      if (elements.isEmpty()) {
        throw ExtractException(RefactoringBundle.message("selected.block.should.represent.a.set.of.statements.or.an.expression"), file)
      }
      val extractOptions = computeWithAnalyzeProgress<ExtractOptions, ExtractException>(project) { findExtractOptions(elements) }
      selectTargetClass(extractOptions) { options ->
        val prepareExtractAction = computeWithAnalyzeProgress<Runnable, Exception>(project) { prepareExtractAction(editor, range, options) }
        prepareExtractAction.run()
      }
    }
    catch (e: ExtractException) {
      val message = JavaRefactoringBundle.message("extract.method.error.prefix") + " " + (e.message ?: "")
      CommonRefactoringUtil.showErrorHint(project, editor, message, ExtractMethodHandler.getRefactoringName(), HelpID.EXTRACT_METHOD)
      showError(editor, e.problems)
    }
  }

  private fun <T, E: Exception> computeWithAnalyzeProgress(project: Project, throwableComputable: ThrowableComputable<T, E>): T {
    return ProgressManager.getInstance().run(object : Task.WithResult<T, E>(project,
      JavaRefactoringBundle.message("dialog.title.analyze.code.fragment.to.extract"), false) {
      override fun compute(indicator: ProgressIndicator): T {
        return ReadAction.compute(throwableComputable)
      }
    })
  }

  private fun prepareExtractAction(editor: Editor, range: TextRange, options: ExtractOptions): Runnable {
    val project = options.project
    val targetClass = options.anchor.containingClass ?: throw IllegalStateException("Failed to find target class")
    val extractor = getDefaultInplaceExtractor(options)
    if (EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled) {
      val popupSettings = createInplaceSettingsPopup(options)
      val guessedNames = suggestSafeMethodNames(options)
      val methodName = guessedNames.first()
      val suggestedNames = guessedNames.takeIf { it.size > 1 }.orEmpty()
      return Runnable {
        executeRefactoringCommand(project) {
          val inplaceExtractor = InplaceMethodExtractor(editor, range, targetClass, extractor, popupSettings, methodName)
          inplaceExtractor.performInplaceRefactoring(LinkedHashSet(suggestedNames))
        }
      }
    }
    else {
      return Runnable {
        extractor.extractInDialog(targetClass, options.elements, "", options.isStatic)
      }
    }
  }

  fun getDefaultInplaceExtractor(options: ExtractOptions): InplaceExtractMethodProvider {
    if (Registry.`is`("java.refactoring.extractMethod.newDuplicatesExtractor")) return DuplicatesMethodExtractor()
    return if (ExtractMethodHandler.canUseNewImpl(options.project, options.anchor.containingFile, options.elements.toTypedArray())) {
      DefaultMethodExtractor()
    } else {
      LegacyMethodExtractor()
    }
  }

  fun suggestSafeMethodNames(options: ExtractOptions): List<String> {
    val unsafeNames = guessMethodName(options)
    val safeNames = unsafeNames.filterNot { name -> hasConflicts(options.copy(methodName = name)) }
    if (safeNames.isNotEmpty()) return safeNames

    val baseName = unsafeNames.firstOrNull() ?: "extracted"
    val generatedNames = sequenceOf(baseName) + generateSequence(1) { seed -> seed + 1 }.map { number -> "$baseName$number" }
    return generatedNames.filterNot { name -> hasConflicts(options.copy(methodName = name)) }.take(1).toList()
  }

  private fun hasConflicts(options: ExtractOptions): Boolean {
    val (_, method) = prepareRefactoringElements(options)
    val conflicts = MultiMap<PsiElement, String>()
    ConflictsUtil.checkMethodConflicts(options.anchor.containingClass, null, method, conflicts)
    return ! conflicts.isEmpty
  }

  fun createInplaceSettingsPopup(options: ExtractOptions): ExtractMethodPopupProvider {
    val isStatic = options.isStatic
    val analyzer = CodeFragmentAnalyzer(options.elements)
    val optionsWithStatic = ExtractMethodPipeline.withForcedStatic(analyzer, options)
    val makeStaticAndPassFields = optionsWithStatic?.inputParameters?.size != options.inputParameters.size
    val showStatic = ! isStatic && optionsWithStatic != null
    val hasAnnotation = options.dataOutput.nullability != Nullability.UNKNOWN && options.dataOutput.type !is PsiPrimitiveType
    val annotationAvailable = ExtractMethodHelper.isNullabilityAvailable(options)
    return ExtractMethodPopupProvider(
      annotateDefault = if (hasAnnotation && annotationAvailable) needsNullabilityAnnotations(options.project) else null,
      makeStaticDefault = if (showStatic) false else null,
      staticPassFields = makeStaticAndPassFields
    )
  }

  fun doDialogExtract(options: ExtractOptions){
    val dialogOptions = mapFromDialog(options, RefactoringBundle.message("extract.method.title"), HelpID.EXTRACT_METHOD)
    if (dialogOptions != null) {
      executeRefactoringCommand(dialogOptions.project) { doRefactoring(dialogOptions) }
    }
  }

  private fun executeRefactoringCommand(project: Project, command: () -> Unit){
    CommandProcessor.getInstance().executeCommand(project, command, ExtractMethodHandler.getRefactoringName(), null)
  }

  private fun doRefactoring(options: ExtractOptions) {
    try {
      sendRefactoringStartedEvent(options.elements.toTypedArray())
      val extractedElements = extractMethod(options)
      sendRefactoringDoneEvent(extractedElements.method)
    }
    catch (e: IncorrectOperationException) {
      LOG.error(e)
    }
  }

  fun replaceElements(sourceElements: List<PsiElement>, callElements: List<PsiElement>, anchor: PsiMember, method: PsiMethod): ExtractedElements {
    return WriteAction.compute<ExtractedElements, Throwable> {
      val addedMethod = anchor.addSiblingAfter(method) as PsiMethod
      val replacedCallElements = replace(sourceElements, callElements)
      ExtractedElements(replacedCallElements, addedMethod)
    }
  }

  fun extractMethod(extractOptions: ExtractOptions): ExtractedElements {
    val elementsToExtract = prepareRefactoringElements(extractOptions)
    return replaceElements(extractOptions.elements, elementsToExtract.callElements, extractOptions.anchor, elementsToExtract.method)
  }

  fun doTestExtract(
    doRefactor: Boolean,
    editor: Editor,
    isConstructor: Boolean?,
    isStatic: Boolean?,
    returnType: PsiType?,
    newNameOfFirstParam: String?,
    targetClass: PsiClass?,
    @PsiModifier.ModifierConstant visibility: String?,
    vararg disabledParameters: Int
  ): Boolean {
    val project = editor.project ?: return false
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return false
    val range = ExtractMethodHelper.findEditorSelection(editor) ?: return false
    val elements = ExtractSelector().suggestElementsToExtract(file, range)
    if (elements.isEmpty()) throw ExtractException("Nothing to extract", file)
    var options = findExtractOptions(elements)
    val analyzer = CodeFragmentAnalyzer(elements)

    val candidates = ExtractMethodPipeline.findTargetCandidates(analyzer, options)
    val defaultTargetClass = candidates.firstOrNull { it !is PsiAnonymousClass } ?: candidates.first()
    options = ExtractMethodPipeline.withTargetClass(analyzer, options, targetClass ?: defaultTargetClass)
                                    ?: throw ExtractException("Fail", elements.first())
    options = options.copy(methodName = "newMethod")
    if (isConstructor != options.isConstructor){
      options = ExtractMethodPipeline.asConstructor(analyzer, options) ?: throw ExtractException("Fail", elements.first())
    }
    if (! options.isStatic && isStatic == true) {
      options = ExtractMethodPipeline.withForcedStatic(analyzer, options) ?: throw ExtractException("Fail", elements.first())
    }
    if (newNameOfFirstParam != null) {
      options = options.copy(
        inputParameters = listOf(options.inputParameters.first().copy(name = newNameOfFirstParam)) + options.inputParameters.drop(1)
      )
    }
    if (returnType != null) {
      options = options.copy(dataOutput = options.dataOutput.withType(returnType))
    }
    if (disabledParameters.isNotEmpty()) {
      options = options.copy(
        disabledParameters = options.inputParameters.filterIndexed { index, _ -> index in disabledParameters },
        inputParameters = options.inputParameters.filterIndexed { index, _ -> index !in disabledParameters }
      )
    }
    if (visibility != null) {
      options = options.copy(visibility = visibility)
    }
    if (options.anchor.containingClass?.isInterface == true) {
      options = ExtractMethodPipeline.adjustModifiersForInterface(options.copy(visibility = PsiModifier.PRIVATE))
    }
    if (doRefactor) {
      extractMethod(options)
    }
    return true
  }

  fun showError(editor: Editor, ranges: List<TextRange>) {
    val project = editor.project ?: return
    if (ranges.isEmpty()) return
    val highlightManager = HighlightManager.getInstance(project)
    ranges.forEach { textRange ->
      highlightManager.addRangeHighlight(editor, textRange.startOffset, textRange.endOffset,
                                         EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null)
    }
    WindowManager.getInstance().getStatusBar(project).info = RefactoringBundle.message("press.escape.to.remove.the.highlighting")
  }

  fun prepareRefactoringElements(extractOptions: ExtractOptions): ExtractedElements {
    val dependencies = withFilteredAnnotations(extractOptions)
    val factory = PsiElementFactory.getInstance(dependencies.project)
    val styleManager = CodeStyleManager.getInstance(dependencies.project)
    val codeBlock = BodyBuilder(factory)
      .build(
        dependencies.elements,
        dependencies.flowOutput,
        dependencies.dataOutput,
        dependencies.inputParameters,
        dependencies.disabledParameters,
        dependencies.requiredVariablesInside
      )
    val method = SignatureBuilder(dependencies.project)
      .build(
        dependencies.anchor.context,
        dependencies.elements,
        dependencies.isStatic,
        dependencies.visibility,
        dependencies.typeParameters,
        dependencies.dataOutput.type.takeIf { !dependencies.isConstructor },
        dependencies.methodName,
        dependencies.inputParameters,
        dependencies.dataOutput.annotations,
        dependencies.thrownExceptions,
        dependencies.anchor
      )
    method.body?.replace(codeBlock)

    val parameters = dependencies.inputParameters.map { it.references.first() }

    val callBuilder = CallBuilder(dependencies.project, dependencies.elements.first())
    val methodCall = callBuilder.createMethodCall(method, parameters).text
    val expressionElement = (dependencies.elements.singleOrNull() as? PsiExpression)
    val callElements = if (expressionElement != null) {
      callBuilder.buildExpressionCall(methodCall, dependencies.dataOutput)
    }
    else {
      callBuilder.buildCall(methodCall, dependencies.flowOutput, dependencies.dataOutput, dependencies.exposedLocalVariables)
    }
    val formattedCallElements = callElements.map { styleManager.reformat(it) }

    if (needsNullabilityAnnotations(dependencies.project) && ExtractMethodHelper.isNullabilityAvailable(dependencies)) {
      updateMethodAnnotations(method, dependencies.inputParameters)
    }

    return ExtractedElements(formattedCallElements, method)
  }

  fun replace(source: List<PsiElement>, target: List<PsiElement>): List<PsiElement> {
    val sourceAsExpression = source.singleOrNull() as? PsiExpression
    val targetAsExpression = target.singleOrNull() as? PsiExpression
    if (sourceAsExpression != null && targetAsExpression != null) {
      val replacedExpression = IntroduceVariableUtil.replace(sourceAsExpression,
                                                             targetAsExpression,
                                                             sourceAsExpression.project)
      return listOf(replacedExpression)
    }

    val normalizedTarget = if (target.size > 1 && source.first().parent !is PsiCodeBlock) {
      wrapWithCodeBlock(target)
    }
    else {
      target
    }
    val replacedElements = normalizedTarget.reversed().map { statement -> source.last().addSiblingAfter(statement) }.reversed()
    source.first().parent.deleteChildRange(source.first(), source.last())
    return replacedElements
  }

  private fun needsNullabilityAnnotations(project: Project): Boolean {
    return PropertiesComponent.getInstance(project).getBoolean(ExtractMethodDialog.EXTRACT_METHOD_GENERATE_ANNOTATIONS, true)
  }

  companion object {
    private val LOG = Logger.getInstance(MethodExtractor::class.java)

    @NonNls const val refactoringId: String = "refactoring.extract.method"

    internal fun sendRefactoringDoneEvent(extractedMethod: PsiMethod) {
      val data = RefactoringEventData()
      data.addElement(extractedMethod)
      extractedMethod.project.messageBus.syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
        .refactoringDone(refactoringId, data)
    }

    internal fun sendRefactoringStartedEvent(elements: Array<PsiElement>) {
      val project = elements.firstOrNull()?.project ?: return
      val data = RefactoringEventData()
      data.addElements(elements)
      val publisher = project.messageBus.syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
      publisher.refactoringStarted(refactoringId, data)
    }
  }
}