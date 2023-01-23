// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.newImpl.inplace.EditorState
import com.intellij.refactoring.extractMethod.newImpl.inplace.ExtractMethodTemplateBuilder
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils
import com.intellij.refactoring.extractMethod.newImpl.inplace.TemplateField
import com.siyeh.ig.psiutils.TypeUtils

data class IntroduceObjectResult(
  val introducedClass: PsiClass,
  val variableDeclaration: PsiVariable,
  val replacedReferences: List<PsiExpression>
)

interface ObjectBuilder {
  fun createClass(): PsiClass
  fun createDeclaration(): PsiDeclarationStatement
  fun createReferenceReplacement(reference: PsiReferenceExpression): PsiExpression
  fun findVariableReferenceInReplacement(replacement: PsiExpression): PsiReferenceExpression?
  fun getAffectedReferences(): List<PsiReferenceExpression>

  companion object {
    fun run(editor: Editor, variables: List<PsiVariable>, scope: List<PsiElement>){
      require(variables.isNotEmpty())
      require(scope.isNotEmpty())
      val objectBuilder: ObjectBuilder = RecordObjectBuilder.create(variables, scope) ?: return
      val file = scope.first().containingFile
      val rangeMarker = file.viewProvider.document.createRangeMarker(scope.first().textRange.startOffset, scope.last().textRange.endOffset)
      rangeMarker.apply { isGreedyToLeft = true; isGreedyToRight = true; }
      val editorState = EditorState(editor)
      WriteCommandAction.writeCommandAction(file.project).run<Throwable> {
        try {
          val (introducedClass, declaration, replacements) = introduceObjectForVariables(objectBuilder, variables, scope.last())
          val introducedVariableReferences = replacements.map { replacement ->
            objectBuilder.findVariableReferenceInReplacement(replacement) ?: throw IllegalStateException()
          }
          ExtractMethodTemplateBuilder(editor, ExtractMethodHandler.getRefactoringName())
            .onBroken { editorState.revert() }
            .createTemplate(file, createTemplateFields(editor, introducedClass, declaration, introducedVariableReferences))
        } catch (e: Throwable) {
          editorState.revert()
          throw e
        }
      }
    }

    private fun introduceObjectForVariables(builder: ObjectBuilder, variables: List<PsiVariable>, placeForDeclaration: PsiElement): IntroduceObjectResult {
      val referenceReplacements = builder.getAffectedReferences()
        .map { reference -> reference.replace(builder.createReferenceReplacement(reference)) as PsiExpression }
      val classAnchor = PsiTreeUtil.getParentOfType(variables.first(), PsiMember::class.java) ?: throw IllegalStateException()
      val introducedClass = classAnchor.addAfter(builder.createClass())
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
      val classReference = (declaration.initializer as? PsiNewExpression)?.classReference?.element ?: throw IllegalStateException()
      val variableName = declaration.nameIdentifier ?: throw IllegalStateException()
      val typeNameField = TemplateField(
        classReference.textRange,
        listOfNotNull(declaration.typeElement, introducedClass.nameIdentifier).map(PsiElement::getTextRange),
        validator = { variableRange -> InplaceExtractUtils.checkClassReference(editor, file, variableRange) }
      )
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
}

class RecordObjectBuilder(private val record: PsiClass, private val references: List<PsiReferenceExpression>): ObjectBuilder {

  companion object {

    fun create(variables: List<PsiVariable>, scope: List<PsiElement>): RecordObjectBuilder? {
      val record = createRecord(variables)
      val affectedReferences = findAffectedReferences(variables, scope.last().nextSibling)
      return RecordObjectBuilder(record, affectedReferences)
    }

    private fun createRecord(variables: List<PsiVariable>): PsiClass {
      require(variables.isNotEmpty())
      val project = variables.first().project
      val factory = PsiElementFactory.getInstance(project)
      val record = factory.createRecord("Result")
      val header = variables.joinToString(separator = ", ") { variable -> "${variable.type.canonicalText} ${variable.name}" }
      record.recordHeader?.replace(factory.createRecordHeaderFromText(header, record))
      return record
    }

    private fun findAffectedReferences(variables: List<PsiVariable>, startingElement: PsiElement?): List<PsiReferenceExpression> {
      val startingPoint = startingElement?.textRange?.startOffset ?: return emptyList()
      return  variables.flatMap { findAffectedReferences(it, startingPoint) }
    }

    private fun findAffectedReferences(variable: PsiVariable, startingOffset: Int): List<PsiReferenceExpression> {
      val references = ReferencesSearch.search(variable)
        .mapNotNull { it.element as? PsiReferenceExpression }
        .filter { reference -> reference.textRange.startOffset >= startingOffset }
        .sortedBy { reference -> reference.textRange.startOffset }
      val firstAssignment = references.find { reference -> PsiUtil.isAccessedForWriting(reference) }
      val endPoint = PsiTreeUtil.getParentOfType(firstAssignment, PsiAssignmentExpression::class.java)?.textRange?.endOffset
      //ReplaceOperatorAssignmentWithAssignmentIntention
      //ReplacePostfixExpressionWithAssignment
      return if (firstAssignment != null && endPoint != null) {
        references.filter { reference -> reference.textRange.endOffset <= endPoint } - firstAssignment
      }
      else {
        references
      }
    }
  }

  override fun createClass(): PsiClass = record

  override fun createDeclaration(): PsiDeclarationStatement {
    val recordComponents = record.recordComponents
    val parameters = recordComponents.joinToString(separator = ",") { component -> component.name }
    val initializer = "new ${record.name}($parameters)"
    val factory = PsiElementFactory.getInstance(record.project)
    val expression = factory.createExpressionFromText(initializer, record)
    return factory.createVariableDeclarationStatement("result", TypeUtils.getType(record), expression)
  }

  override fun createReferenceReplacement(reference: PsiReferenceExpression): PsiExpression {
    return PsiElementFactory.getInstance(reference.project).createExpressionFromText("result.${reference.text}()", reference.context)
  }

  override fun findVariableReferenceInReplacement(replacement: PsiExpression): PsiReferenceExpression? {
    val place = replacement.textRange.startOffset
    return PsiTreeUtil.findElementOfClassAtOffset(replacement.containingFile, place, PsiReferenceExpression::class.java, false)
  }

  override fun getAffectedReferences(): List<PsiReferenceExpression> = references
}