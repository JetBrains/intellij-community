// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl.parameterObject

import com.intellij.codeInsight.hint.EditorCodePreview
import com.intellij.codeInsight.hint.HintManager
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.pom.java.JavaFeature
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor
import com.intellij.refactoring.extractMethod.newImpl.inplace.EditorState
import com.intellij.refactoring.extractMethod.newImpl.inplace.ExtractMethodTemplateBuilder
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createGreedyRangeMarker
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.showExtractErrorHint
import com.intellij.refactoring.extractMethod.newImpl.inplace.TemplateField

private data class IntroduceObjectResult(
  val introducedClass: PsiClass,
  val variableDeclaration: PsiVariable,
  val replacedReferences: List<PsiExpression>
)

/**
 * Creates a class or record to wrap multiple variables inside a single instance.
 * Used as a first step before extracting a method from a code fragment with multiple results.
 */
internal object ResultObjectExtractor {
  suspend fun run(editor: Editor, variables: List<PsiVariable>, scope: List<PsiElement>){
    require(variables.isNotEmpty())
    require(scope.isNotEmpty())

    val affectedReferences = readAction { ParameterObjectUtils.findAffectedReferences(variables, scope) }
    if (affectedReferences == null) {

      showExtractErrorHint(editor, JavaRefactoringBundle.message("extract.method.error.many.outputs"), variables.map { it.textRange })
      return
    }
    val shouldInsertRecord = readAction { PsiUtil.isAvailable(JavaFeature.RECORDS, variables.first()) }
    val objectBuilder = if (shouldInsertRecord) {
      readAction { RecordResultObjectBuilder.create(variables) }
    } else {
      readAction { ClassResultObjectBuilder.create(variables) }
    }
    val file = readAction { scope.first().containingFile }
    val project = file.project
    val extractRange = readAction {
      createGreedyRangeMarker (file.viewProvider.document, scope.first().textRange.union(scope.last().textRange))
    }
    val editorState = readAction { EditorState(project, editor) }
    val disposable = Disposer.newDisposable()
    ExtractMethodHelper.mergeWriteCommands(editor, disposable, ExtractMethodHandler.getRefactoringName())
    writeCommandAction(project, ExtractMethodHandler.getRefactoringName()) {
      try {
        val (introducedClass, declaration, replacements) = introduceObjectForVariables(objectBuilder, variables, affectedReferences, scope.last())
        val introducedVariableReferences = replacements.map { replacement ->
          objectBuilder.findVariableReferenceInReplacement(replacement) ?: throw IllegalStateException()
        }
        val preview = createPreview(editor, introducedClass, declaration, replacements)
        Disposer.register(disposable, preview)
        ExtractMethodTemplateBuilder(editor, ExtractMethodHandler.getRefactoringName())
          .onBroken { editorState.revert() }
          .onSuccess { invokeLater { MethodExtractor ().doExtract(file, extractRange.textRange) } }
          .disposeWithTemplate(disposable)
          .createTemplate(file, createTemplateFields(editor, introducedClass, declaration, introducedVariableReferences))
        val objectType = if (shouldInsertRecord) {
          JavaRefactoringBundle.message("extract.method.error.wrap.many.outputs.record")
        }
        else {
          JavaRefactoringBundle.message("extract.method.error.wrap.many.outputs.class")
        }
        val message = JavaRefactoringBundle.message("extract.method.error.wrap.many.outputs", objectType)
        HintManager.getInstance().showInformationHint(editor, message)
      } catch (e: Throwable) {
        Disposer.dispose(disposable)
        editorState.revert()
        throw e
      }
    }
  }

  private fun createPreview(editor: Editor, introducedClass: PsiClass, declaration: PsiVariable, replacedReferences: List<PsiExpression>): EditorCodePreview {
    val preview = EditorCodePreview.create(editor)
    val classHighlighting = InplaceExtractUtils.createInsertedHighlighting(editor, introducedClass.textRange)
    Disposer.register(preview, classHighlighting)
    val rangeToNavigate = introducedClass.nameIdentifier?.textRange?.endOffset ?: introducedClass.textRange.startOffset
    val classLines = InplaceExtractUtils.getLinesFromTextRange(editor.document, introducedClass.textRange, maxLength = 4)
    InplaceExtractUtils.addPreview(preview, editor, classLines, rangeToNavigate)
    val declarationHighlighting = InplaceExtractUtils.createInsertedHighlighting(editor, declaration.textRange)
    Disposer.register(preview, declarationHighlighting)
    val declarationLines = InplaceExtractUtils.getLinesFromTextRange(editor.document, declaration.textRange)
    preview.addPreview(declarationLines, onClickAction = { InplaceExtractUtils.navigateToTemplateVariable(editor) })
    replacedReferences.forEach { replacement ->
      val highlighting = InplaceExtractUtils.createCodeHighlighting(editor, replacement.textRange, DiffColors.DIFF_MODIFIED)
      Disposer.register(preview, highlighting)
    }
    return preview
  }

  private fun introduceObjectForVariables(builder: ResultObjectBuilder,
                                          variables: List<PsiVariable>,
                                          affectedReferences: List<PsiReferenceExpression>,
                                          placeForDeclaration: PsiElement): IntroduceObjectResult {
    val referenceReplacements = affectedReferences
      .map { reference -> reference.replace(builder.createReferenceReplacement(reference)) as PsiExpression }
    val classAnchor = PsiTreeUtil.getParentOfType(variables.first(), PsiMember::class.java) ?: throw IllegalStateException()
    val psiClass = builder.createClass()
    psiClass.modifierList?.setModifierProperty(PsiModifier.PRIVATE, true)
    InplaceExtractUtils.findTypeParameters(variables.map(PsiVariable::getType)).forEach { typeParameter ->
      psiClass.typeParameterList?.add(typeParameter)
    }
    val introducedClass = classAnchor.addAfter(psiClass)
    val declaration = placeForDeclaration.addAfter(builder.createDeclaration())
    val variableDeclaration = declaration.declaredElements.first() as PsiVariable

    val variablePointer = SmartPointerManager.createPointer(variableDeclaration)
    val classPointer = SmartPointerManager.createPointer(introducedClass)
    val replacedPointers = referenceReplacements.map(SmartPointerManager::createPointer)
    val document = placeForDeclaration.containingFile.viewProvider.document
    PsiDocumentManager.getInstance(declaration.project).doPostponedOperationsAndUnblockDocument(document)
    return IntroduceObjectResult(
      classPointer.element ?: throw IllegalStateException(),
      variablePointer.element ?: throw IllegalStateException(),
      replacedPointers.map { pointer -> pointer.element ?: throw IllegalStateException() }
    )
  }

  private fun createTemplateFields(editor: Editor,
                                   introducedClass: PsiClass,
                                   declaration: PsiVariable,
                                   referencesToDeclaration: List<PsiReferenceExpression>): List<TemplateField> {
    val file = introducedClass.containingFile
    val classReference = (declaration.initializer as? PsiNewExpression)?.classReference?.referenceNameElement ?: throw IllegalStateException()
    val constructorIdentifiers = introducedClass.constructors.mapNotNull { method -> method.nameIdentifier }
    val declarationTypeIdentifier = declaration.typeElement?.innermostComponentReferenceElement?.referenceNameElement
    val typeIdentifiersToUpdate = listOfNotNull(declarationTypeIdentifier, introducedClass.nameIdentifier) + constructorIdentifiers
    val typeNameField = TemplateField(
      classReference.textRange,
      typeIdentifiersToUpdate.map(PsiElement::getTextRange),
      validator = { variableRange -> InplaceExtractUtils.checkClassReference(editor, file, variableRange) }
    )
    val variableName = declaration.nameIdentifier ?: throw IllegalStateException()
    val variableNameField = TemplateField(
      variableName.textRange,
      referencesToDeclaration.map(PsiElement::getTextRange),
      validator = { variableRange -> InplaceExtractUtils.checkVariableIdentifier(editor, file, variableRange) }
    )
    return listOf(typeNameField, variableNameField)
  }

  private inline fun <reified T: PsiElement> PsiElement.addAfter(element: T): T {
    return parent.addAfter(element, this) as T
  }
}