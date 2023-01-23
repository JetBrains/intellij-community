// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.newImpl.inplace.ExtractMethodTemplateBuilder
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils
import com.intellij.refactoring.extractMethod.newImpl.inplace.TemplateField
import com.siyeh.ig.psiutils.TypeUtils

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
      val project = variables.first().project
      WriteCommandAction.writeCommandAction(project).run<Throwable> {
        val referenceReplacements = objectBuilder.getAffectedReferences()
          .map { reference -> reference.replace(objectBuilder.createReferenceReplacement(reference)) as PsiExpression }
        val classAnchor = PsiTreeUtil.getParentOfType(variables.first(), PsiMethod::class.java) ?: throw IllegalStateException()
        val addedClass = classAnchor.addAfter(objectBuilder.createClass())
        val declarationAnchor = scope.last()
        val variableDeclaration = declarationAnchor.addAfter(objectBuilder.createDeclaration())
        val variable = variableDeclaration.declaredElements.first() as PsiVariable

        runTemplate(editor, addedClass, variable, referenceReplacements, objectBuilder)
      }
    }

    private fun runTemplate(editor: Editor, introducedClass: PsiClass, declaration: PsiVariable, replacedReferences: List<PsiExpression>, objectBuilder: ObjectBuilder) {
      val declarationPointer = SmartPointerManager.createPointer(declaration)
      val classPointer = SmartPointerManager.createPointer(introducedClass)
      val replacedPointers = replacedReferences.map(SmartPointerManager::createPointer)
      PsiDocumentManager.getInstance(declaration.project).doPostponedOperationsAndUnblockDocument(editor.document)

      val file = classPointer.element?.containingFile ?: throw IllegalStateException()
      val variable = declarationPointer.element
      val classReference = (variable?.initializer as? PsiNewExpression)?.classReference?.element
      val declarationType = variable?.typeElement
      val classIdentifier = classPointer.element?.nameIdentifier
      val variableName = variable?.nameIdentifier
      val variableReferences = replacedPointers
        .mapNotNull{ replacementPointer -> replacementPointer.element }
        .mapNotNull{ replacedReference -> objectBuilder.findVariableReferenceInReplacement(replacedReference) }
      val fields = listOf(
        TemplateField(
          classReference!!.textRange,
          listOfNotNull(declarationType, classIdentifier).map(PsiElement::getTextRange),
          validator = { variableRange -> InplaceExtractUtils.checkClassReference(editor, file, variableRange) }
        ),
        TemplateField(
          variableName!!.textRange,
          variableReferences.map(PsiElement::getTextRange),
          validator = { variableRange -> InplaceExtractUtils.checkVariableIdentifier(editor, file, variableRange) }
        )
      )
      ExtractMethodTemplateBuilder(editor, ExtractMethodHandler.getRefactoringName())
        .createTemplate(declaration.containingFile, fields)
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