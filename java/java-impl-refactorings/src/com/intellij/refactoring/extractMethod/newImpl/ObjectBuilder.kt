// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature
import com.intellij.codeInsight.hint.EditorCodePreview
import com.intellij.codeInsight.hint.HintManager
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.newImpl.inplace.EditorState
import com.intellij.refactoring.extractMethod.newImpl.inplace.ExtractMethodTemplateBuilder
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.addPreview
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createCodeHighlighting
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createGreedyRangeMarker
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.getLinesFromTextRange
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.navigateToTemplateVariable
import com.intellij.refactoring.extractMethod.newImpl.inplace.TemplateField
import com.siyeh.ig.psiutils.TypeUtils

data class IntroduceObjectResult(
  val introducedClass: PsiClass,
  val variableDeclaration: PsiVariable,
  val replacedReferences: List<PsiExpression>
)

enum class Chooser {
  Yes, No;
}

interface ObjectBuilder {
  fun createClass(): PsiClass
  fun createDeclaration(): PsiDeclarationStatement
  fun createReferenceReplacement(reference: PsiReferenceExpression): PsiExpression
  fun findVariableReferenceInReplacement(replacement: PsiExpression): PsiReferenceExpression?
  fun getAffectedReferences(): List<PsiReferenceExpression>

  companion object {
    fun run(editor: Editor, variables: List<PsiVariable>, scope: List<PsiElement>){
      val message = JavaRefactoringBundle.message("extract.method.error.wrap.many.outputs")
      HintManager.getInstance().showErrorHint(editor, message)
      JBPopupFactory.getInstance()
        .createPopupChooserBuilder(listOf(Chooser.Yes, Chooser.No))
        .setItemChosenCallback { item ->
          if (item == Chooser.Yes) {
            doExtract(editor, variables.sortedBy { variable -> variable.textRange.startOffset }, scope)
          }
        }
        .setMovable(true)
        .setResizable(false)
        .setRequestFocus(true)
        .createPopup()
        .showInBestPositionFor(editor)
    }

    fun doExtract(editor: Editor, variables: List<PsiVariable>, scope: List<PsiElement>){
      require(variables.isNotEmpty())
      require(scope.isNotEmpty())

      val objectBuilder = if (HighlightingFeature.RECORDS.isAvailable(variables.first())) {
        RecordObjectBuilder.create(variables, scope)
      } else {
        ClassObjectBuilder.create(variables, scope)
      }
      val file = scope.first().containingFile
      val extractRange = createGreedyRangeMarker(file.viewProvider.document, scope.first().textRange.union(scope.last().textRange))
      val editorState = EditorState(editor)
      WriteCommandAction.writeCommandAction(file.project).run<Throwable> {
        try {
          val (introducedClass, declaration, replacements) = introduceObjectForVariables(objectBuilder, variables, scope.last())
          val introducedVariableReferences = replacements.map { replacement ->
            objectBuilder.findVariableReferenceInReplacement(replacement) ?: throw IllegalStateException()
          }
          val disposable = Disposer.newDisposable()
          val preview = createPreview(editor, introducedClass, declaration, replacements)
          Disposer.register(disposable, preview)
          ExtractMethodTemplateBuilder(editor, ExtractMethodHandler.getRefactoringName())
            .onBroken { editorState.revert() }
            .onSuccess { invokeLater { MethodExtractor ().doExtract(file, extractRange.textRange) } }
            .disposeWithTemplate(disposable)
            .createTemplate(file, createTemplateFields(editor, introducedClass, declaration, introducedVariableReferences))
        } catch (e: Throwable) {
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
      val classLines = getLinesFromTextRange(editor.document, introducedClass.textRange, maxLength = 4)
      addPreview(preview, editor, classLines, rangeToNavigate)
      val declarationHighlighting = InplaceExtractUtils.createInsertedHighlighting(editor, declaration.textRange)
      Disposer.register(preview, declarationHighlighting)
      val declarationLines = getLinesFromTextRange(editor.document, declaration.textRange)
      preview.addPreview(declarationLines, onClickAction = { navigateToTemplateVariable(editor) })
      replacedReferences.forEach { replacement ->
        val highlighting = createCodeHighlighting(editor, replacement.textRange, DiffColors.DIFF_MODIFIED)
        Disposer.register(preview, highlighting)
      }
      return preview
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
      val constructorIdentifiers = introducedClass.constructors.mapNotNull { method -> method.nameIdentifier }
      val typeIdentifiersToUpdate = listOfNotNull(declaration.typeElement, introducedClass.nameIdentifier) + constructorIdentifiers
      val typeNameField = TemplateField(
        classReference.textRange,
        typeIdentifiersToUpdate.map(PsiElement::getTextRange),
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

    fun create(variables: List<PsiVariable>, scope: List<PsiElement>): RecordObjectBuilder {
      val record = createRecord(variables)
      val affectedReferences = findAffectedReferences(variables, scope.last().nextSibling)
      return RecordObjectBuilder(record, affectedReferences)
    }

    private fun createRecord(variables: List<PsiVariable>): PsiClass {
      require(variables.isNotEmpty())
      val project = variables.first().project
      val factory = PsiElementFactory.getInstance(project)
      val record = factory.createRecord("Result")
      val typeParameters = variables.mapNotNull { variable -> variable.type as? PsiClassReferenceType }.filterNot { type -> type.isRaw }
      typeParameters.forEach { type ->
        val typeParameter = factory.createTypeParameter(type.name, emptyArray())
        record.typeParameterList?.add(typeParameter)
      }
      val header = variables.joinToString(separator = ", ") { variable -> "${variable.type.canonicalText} ${variable.name}" }
      record.recordHeader?.replace(factory.createRecordHeaderFromText(header, record))
      return record
    }

    fun findAffectedReferences(variables: List<PsiVariable>, startingElement: PsiElement?): List<PsiReferenceExpression> {
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

class ClassObjectBuilder(private val pojoClass: PsiClass, private val references: List<PsiReferenceExpression>): ObjectBuilder{
  companion object {
    fun create(variables: List<PsiVariable>, scope: List<PsiElement>): ClassObjectBuilder {
      val pojoClass = createPojoClass(variables)
      val affectedReferences = RecordObjectBuilder.findAffectedReferences(variables, scope.last().nextSibling)
      return ClassObjectBuilder(pojoClass, affectedReferences)
    }

    private fun createPojoClass(variables: List<PsiVariable>): PsiClass {
      val project = variables.first().project
      val factory = PsiElementFactory.getInstance(project)
      val pojoClass = factory.createClass("Result")
      val typeParameters = variables.mapNotNull { variable -> variable.type as? PsiClassReferenceType }.filterNot { type -> type.isRaw }
      typeParameters.forEach { type ->
        val typeParameter = factory.createTypeParameter(type.name, emptyArray())
        pojoClass.typeParameterList?.add(typeParameter)
      }
      variables.forEach { variable ->
        val field = factory.createField(variable.name!!, variable.type)
        field.modifierList?.setModifierProperty(PsiModifier.PUBLIC, true)
        field.modifierList?.setModifierProperty(PsiModifier.FINAL, true)
        pojoClass.add(field)
      }
      val constructor = factory.createConstructor()
      val body = factory.createCodeBlock()
      variables.forEach { variable ->
        val parameter = factory.createParameter(variable.name!!, variable.type)
        val assignment = factory.createStatementFromText("this.${variable.name} = ${variable.name};", constructor)
        constructor.parameterList.add(parameter)
        body.add(assignment)
      }
      constructor.body?.replace(body)
      pojoClass.add(constructor)
      pojoClass.modifierList?.setModifierProperty(PsiModifier.STATIC, true)
      return pojoClass
    }
  }

  override fun createClass(): PsiClass = pojoClass

  override fun createDeclaration(): PsiDeclarationStatement {
    val fields = pojoClass.allFields
    val parameters = fields.joinToString(separator = ",") { field -> field.name }
    val initializer = "new ${pojoClass.name}($parameters)"
    val factory = PsiElementFactory.getInstance(pojoClass.project)
    val expression = factory.createExpressionFromText(initializer, pojoClass)
    return factory.createVariableDeclarationStatement("result", TypeUtils.getType(pojoClass), expression)
  }

  override fun createReferenceReplacement(reference: PsiReferenceExpression): PsiExpression {
    return PsiElementFactory.getInstance(reference.project).createExpressionFromText("result.${reference.text}", reference.context)
  }

  override fun findVariableReferenceInReplacement(replacement: PsiExpression): PsiReferenceExpression? {
    val place = replacement.textRange.startOffset
    return PsiTreeUtil.findElementOfClassAtOffset(replacement.containingFile, place, PsiReferenceExpression::class.java, false)
  }

  override fun getAffectedReferences(): List<PsiReferenceExpression> = references
}