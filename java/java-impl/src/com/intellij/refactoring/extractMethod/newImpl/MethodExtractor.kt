// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractMethod.ExtractMethodDialog
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.addSiblingAfter
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.wrapWithCodeBlock
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodPipeline.selectTargetClass
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodPipeline.withFilteredAnnotations
import com.intellij.refactoring.extractMethod.newImpl.MapFromDialog.mapFromDialog
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.util.IncorrectOperationException

class MethodExtractor {

  private val LOG = Logger.getInstance(MethodExtractor::class.java)

  fun doInplaceExtract(editor: Editor, file: PsiFile, range: TextRange) {
    val project = editor.project ?: return
    val statements = ExtractSelector().suggestElementsToExtract(file, range)
    val extractOptions = findExtractOptions(statements)

    fun command() {
      ApplicationManager.getApplication().runWriteAction{
        val (method, callElements) = extractMethod(extractOptions)
        val callExpression = PsiTreeUtil.findChildOfType(callElements.first(), PsiMethodCallExpression::class.java)!!
        editor.caretModel.moveToOffset(callExpression.textOffset)
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
        val inplaceExtractor = InplaceMethodExtractor(project, editor)
        inplaceExtractor.setElementToRename(method)
        inplaceExtractor.performInplaceRefactoring(linkedSetOf())
      }
    }
    CommandProcessor.getInstance().executeCommand(project, ::command, ExtractMethodHandler.getRefactoringName(), null)
  }

  fun doExtract(file: PsiFile, range: TextRange, refactoringName: String, helpId: String) {
    val project = file.project
    val editor = PsiEditorUtil.findEditor(file) ?: return

    if (Registry.`is`("java.refactoring.extractMethod.inplace")) {
      doInplaceExtract(editor, file, range)
      return
    }

    try {
      val statements = ExtractSelector().suggestElementsToExtract(file, range)
      if (!CommonRefactoringUtil.checkReadOnlyStatus(file.project, file)) return
      if (statements.isEmpty()) {
        throw ExtractException(RefactoringBundle.message("selected.block.should.represent.a.set.of.statements.or.an.expression"), file)
      }
      val extractOptions = findExtractOptions(statements)
      selectTargetClass(extractOptions) { targetOptions ->
        val options = mapFromDialog(targetOptions, refactoringName, helpId)
        if (options != null) {
          fun command() = PostprocessReformattingAspect.getInstance(project).postponeFormattingInside { doRefactoring(options) }
          CommandProcessor.getInstance().executeCommand(project, ::command, ExtractMethodHandler.getRefactoringName(), null)
        }
      }
    }
    catch (e: ExtractException) {
      val message = JavaRefactoringBundle.message("extract.method.error.prefix") + " " + (e.message ?: "")
      CommonRefactoringUtil.showErrorHint(project, editor, message, refactoringName, HelpID.EXTRACT_METHOD)
      showError(editor, e.problems)
    }
  }

  private fun doRefactoring(options: ExtractOptions) {
    try {
      val beforeData = RefactoringEventData()
      beforeData.addElements(options.elements.toTypedArray())
      options.project.messageBus.syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
        .refactoringStarted("refactoring.extract.method", beforeData)
      val (method, _) = extractMethod(options)
      val data = RefactoringEventData()
      data.addElement(method)
      options.project.messageBus.syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
        .refactoringDone("refactoring.extract.method", data)
    }
    catch (e: IncorrectOperationException) {
      LOG.error(e)
    }
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

  fun extractMethod(extractOptions: ExtractOptions): Pair<PsiMethod, List<PsiElement>> {
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

    val parameters = dependencies.inputParameters.map { it.references.first() }.joinToString { it.text }
    val methodCall = findExtractQualifier(dependencies) + "(" + parameters + ")"

    val callBuilder = CallBuilder(dependencies.project, dependencies.elements.first().context)
    val expressionElement = (dependencies.elements.singleOrNull() as? PsiExpression)
    val callElements = if (expressionElement != null) {
      callBuilder.buildExpressionCall(methodCall, dependencies.dataOutput)
    } else {
      callBuilder.buildCall(methodCall, dependencies.flowOutput, dependencies.dataOutput, dependencies.exposedLocalVariables)
    }
    val formattedCallElements = callElements.map { styleManager.reformat(it) }

    if (needsNullabilityAnnotations(dependencies.project)) {
      updateMethodAnnotations(method, dependencies.inputParameters)
    }

    var addedMethod: PsiMethod? = null
    var replacedElements: List<PsiElement> = emptyList()
    ApplicationManager.getApplication().runWriteAction {
      addedMethod = dependencies.anchor.addSiblingAfter(method) as PsiMethod
      replacedElements = replace(dependencies.elements, formattedCallElements)
    }

    return Pair(addedMethod!!, replacedElements)
  }

  private fun replace(source: List<PsiElement>, target: List<PsiElement>): List<PsiElement> {
    val sourceAsExpression = source.singleOrNull() as? PsiExpression
    val targetAsExpression = target.singleOrNull() as? PsiExpression
    if (sourceAsExpression != null && targetAsExpression != null) {
      val replacedExpression = IntroduceVariableBase.replace(sourceAsExpression, targetAsExpression, sourceAsExpression.project)
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
}

private fun findExtractQualifier(options: ExtractOptions): String {
  val callText = options.methodName + "(" + options.inputParameters.map { it.references.first() }.joinToString { it.text } + ")"
  val factory = PsiElementFactory.getInstance(options.project)
  val callElement = factory.createExpressionFromText(callText, options.elements.first().context) as PsiMethodCallExpression
  val targetClassName = options.anchor.containingClass?.name
  val member = findClassMember(options.elements.first())
  if (member == options.anchor) return options.methodName
  return if (callElement.resolveMethod() != null && !options.isConstructor) {
    if (options.isStatic) "$targetClassName.${options.methodName}" else "$targetClassName.this.${options.methodName}"
  }
  else {
    options.methodName
  }
}