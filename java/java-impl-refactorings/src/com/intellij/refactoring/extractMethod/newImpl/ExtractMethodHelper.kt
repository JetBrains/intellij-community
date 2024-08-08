// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.Nullability
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.codeInsight.intention.AddAnnotationPsiFix
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.impl.FinishMarkAction
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.Command
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.impl.source.codeStyle.JavaCodeStyleManagerImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.IntroduceVariableUtil
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter
import com.intellij.refactoring.introduceField.ElementToWorkOn
import com.intellij.util.CommonJavaRefactoringUtil
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ExtractMethodHelper {

  fun hasReferencesToScope(scope: List<PsiElement>, elements: List<PsiElement>): Boolean {
    val localVariables = scope.asSequence().flatMap { element -> PsiTreeUtil.findChildrenOfType(element, PsiVariable::class.java) }.toSet()
    return elements.asSequence()
      .flatMap { element -> PsiTreeUtil.findChildrenOfType(element, PsiReferenceExpression::class.java) }
      .mapNotNull { reference -> reference.resolve() as? PsiVariable }
      .any { variable -> variable in localVariables }
  }

  @JvmStatic
  fun findEditorSelection(editor: Editor): TextRange? {
    val selectionModel = editor.selectionModel
    return if (selectionModel.hasSelection()) TextRange(selectionModel.selectionStart, selectionModel.selectionEnd) else null
  }

  fun isNullabilityAvailable(extractOptions: ExtractOptions): Boolean {
    val project = extractOptions.project
    val scope = extractOptions.elements.first().resolveScope
    val defaultNullable = NullableNotNullManager.getInstance(project).defaultNullable
    val annotationClass = JavaPsiFacade.getInstance(project).findClass(defaultNullable, scope)
    return annotationClass != null
  }

  private fun wrapWithCodeBlock(elements: List<PsiElement>): List<PsiCodeBlock> {
    require(elements.isNotEmpty())
    val codeBlock = PsiElementFactory.getInstance(elements.first().project).createCodeBlock()
    elements.forEach { codeBlock.add(it) }
    return listOf(codeBlock)
  }

  fun getReturnedExpression(returnOrYieldStatement: PsiStatement): PsiExpression? {
    return when (returnOrYieldStatement) {
      is PsiReturnStatement -> returnOrYieldStatement.returnValue
      is PsiYieldStatement -> returnOrYieldStatement.expression
      else -> null
    }
  }

  fun findRequiredTypeParameters(targetContext: PsiClass?, searchScope: List<PsiElement>): List<PsiTypeParameter> {
    fun findElementWithTypeParameter(element: PsiElement) = PsiTreeUtil.getContextOfType(element, PsiTypeParameterListOwner::class.java)
    val availableTypeParameters = generateSequence (targetContext, ::findElementWithTypeParameter)
      .flatMap { typeListOwner -> typeListOwner.typeParameterList?.typeParameters.orEmpty().toList() }
      .toSet()
    return findUsedTypeParameters(searchScope).filter { parameter -> parameter !in availableTypeParameters }
  }

  private fun findUsedTypeParameters(searchScope: List<PsiElement>): List<PsiTypeParameter> {
    val usedTypeParameters = mutableSetOf<PsiTypeParameter>()
    searchScope.forEach { element ->
      CommonJavaRefactoringUtil.collectTypeParameters(usedTypeParameters, element)
    }
    CommonJavaRefactoringUtil.collectTypeParametersInDependencies(Conditions.alwaysTrue(), usedTypeParameters)
    return usedTypeParameters.sortedBy { parameter -> parameter.textRange.startOffset }
  }

  fun inputParameterOf(externalReference: ExternalReference): InputParameter {
    return InputParameter(externalReference.references, requireNotNull(externalReference.variable.name), externalReference.variable.type)
  }

  fun inputParameterOf(expressionGroup: List<PsiExpression>): InputParameter {
    require(expressionGroup.isNotEmpty())
    val expression = expressionGroup.first()
    val objectType = PsiType.getJavaLangObject(expression.manager, GlobalSearchScope.projectScope(expression.project))
    return InputParameter(expressionGroup, guessName(expression), expressionGroup.first().type ?: objectType)
  }

  fun PsiElement.addSiblingAfter(element: PsiElement): PsiElement {
    return this.parent.addAfter(element, this)
  }

  fun getValidParentOf(element: PsiElement): PsiElement {
    val physicalParent = when (val parent = element.parent) {
      is DummyHolder -> parent.context
      null -> element.context
      else -> parent
    }
    return physicalParent ?: throw IllegalArgumentException()
  }

  fun addNullabilityAnnotation(typeElement: PsiTypeElement?, nullability: Nullability) {
    if (typeElement == null) return
    val nullabilityManager = NullableNotNullManager.getInstance(typeElement.project)
    val annotation = when (nullability) {
      Nullability.NOT_NULL -> nullabilityManager.defaultNotNull
      Nullability.NULLABLE -> nullabilityManager.defaultNullable
      else -> return
    }
    val annotationElement = AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(annotation, PsiNameValuePair.EMPTY_ARRAY, typeElement)
    if (annotationElement != null) {
      JavaCodeStyleManager.getInstance(typeElement.project).shortenClassReferences(annotationElement)
    }
  }

  private fun findVariableReferences(element: PsiElement): Sequence<PsiVariable> {
    val references = PsiTreeUtil.findChildrenOfAnyType(element, PsiReferenceExpression::class.java)
    return references.asSequence().mapNotNull { reference -> (reference.resolve() as? PsiVariable) }
  }

  private fun hasConflictResolve(name: String?, scopeToIgnore: List<PsiElement>): Boolean {
    require(scopeToIgnore.isNotEmpty())
    if (name == null) return false
    val lastElement = scopeToIgnore.last()
    val helper = JavaPsiFacade.getInstance(lastElement.project).resolveHelper
    val resolvedRange = helper.resolveAccessibleReferencedVariable(name, lastElement.context)?.textRange ?: return false
    return resolvedRange !in TextRange(scopeToIgnore.first().textRange.startOffset, scopeToIgnore.last().textRange.endOffset)
  }

  fun uniqueNameOf(name: String?, scopeToIgnore: List<PsiElement>, reservedNames: List<String>): String? {
    require(scopeToIgnore.isNotEmpty())
    if (name == null) return null
    val lastElement = scopeToIgnore.last()
    if (hasConflictResolve(name, scopeToIgnore) || name in reservedNames){
      val styleManager = JavaCodeStyleManager.getInstance(lastElement.project) as JavaCodeStyleManagerImpl
      return styleManager.suggestUniqueVariableName(name, lastElement, true)
    } else {
      return name
    }
  }

  fun guessName(expression: PsiExpression): String {
    val codeStyleManager = JavaCodeStyleManager.getInstance(expression.project) as JavaCodeStyleManagerImpl

    return findVariableReferences(expression).mapNotNull { variable -> variable.name }.firstOrNull()
           ?: codeStyleManager.suggestSemanticNames(expression).firstOrNull()
           ?: "x"
  }

  fun createDeclaration(variable: PsiVariable): PsiDeclarationStatement {
    val factory = PsiElementFactory.getInstance(variable.project)
    val declaration = factory.createVariableDeclarationStatement(requireNotNull(variable.name), variable.type, null)
    val declaredVariable = declaration.declaredElements.first() as PsiVariable
    PsiUtil.setModifierProperty(declaredVariable, PsiModifier.FINAL, variable.hasModifierProperty(PsiModifier.FINAL))
    variable.annotations.forEach { annotation -> declaredVariable.modifierList?.add(annotation) }
    return declaration
  }

  fun getExpressionType(expression: PsiExpression): PsiType {
    val type = CommonJavaRefactoringUtil.getTypeByExpressionWithExpectedType(expression)
    return when {
      type != null -> type
      expression.parent is PsiExpressionStatement -> PsiTypes.voidType()
      else -> PsiType.getJavaLangObject(expression.manager, GlobalSearchScope.allScope(expression.project))
    }
  }

  fun areSame(elements: List<PsiElement?>): Boolean {
    val first = elements.firstOrNull()
    return elements.all { element -> areSame(first, element) }
  }

  fun areSame(first: PsiElement?, second: PsiElement?): Boolean {
    return when {
      first != null && second != null -> PsiEquivalenceUtil.areElementsEquivalent(first, second)
      first == null && second == null -> true
      else -> false
    }
  }

  private fun boxedTypeOf(type: PsiType, context: PsiElement): PsiType {
    return (type as? PsiPrimitiveType)?.getBoxedType(context) ?: type
  }

  fun PsiModifierListOwner?.hasExplicitModifier(modifier: String): Boolean {
    return this?.modifierList?.hasExplicitModifier(modifier) == true
  }

  fun DataOutput.withBoxedType(): DataOutput {
    return when (this) {
      is VariableOutput -> copy(type = boxedTypeOf(type, variable))
      is ExpressionOutput -> copy(type = boxedTypeOf(type, returnExpressions.first()))
      ArtificialBooleanOutput, is EmptyOutput -> this
    }
  }

  fun areSemanticallySame(statements: List<PsiStatement>): Boolean {
    if (statements.isEmpty()) return true
    if (! areSame(statements)) return false
    val returnExpressions = statements.mapNotNull { statement -> (statement as? PsiReturnStatement)?.returnValue }
    return returnExpressions.all { expression -> PsiUtil.isConstantExpression(expression) || expression.type == PsiTypes.nullType() }
  }

  fun haveReferenceToScope(elements: List<PsiElement>, scope: List<PsiElement>): Boolean {
    val scopeRange = TextRange(scope.first().textRange.startOffset, scope.last().textRange.endOffset)
    return elements.asSequence()
      .flatMap { PsiTreeUtil.findChildrenOfAnyType(it, false, PsiJavaCodeReferenceElement::class.java).asSequence() }
      .mapNotNull { reference -> reference.resolve() }
      .any{ referencedElement -> referencedElement.textRange in scopeRange }
  }

  fun guessMethodName(options: ExtractOptions): List<String> {
    val project = options.project
    val variableNames: MutableSet<String> = LinkedHashSet()
    val codeStyleManager = JavaCodeStyleManager.getInstance(project) as JavaCodeStyleManagerImpl
    val returnType = options.dataOutput.type

    val expression = options.elements.singleOrNull() as? PsiExpression
    if (expression != null || returnType !is PsiPrimitiveType) {
      codeStyleManager.suggestVariableName(VariableKind.FIELD, null, expression, returnType).names
        .forEach { name ->
          variableNames += codeStyleManager.variableNameToPropertyName(name, VariableKind.FIELD)
        }
    }

    val outVariable = (options.dataOutput as? VariableOutput)?.variable
    if (outVariable != null) {
      val outKind = codeStyleManager.getVariableKind(outVariable)
      val propertyName = codeStyleManager.variableNameToPropertyName(outVariable.name!!, outKind)
      val names = codeStyleManager.suggestVariableName(VariableKind.FIELD, propertyName, null, outVariable.type).names
      names.forEach { name ->
        variableNames += codeStyleManager.variableNameToPropertyName(name, VariableKind.FIELD)
      }
    }

    val prefix = if (returnType == PsiTypes.booleanType()) "is" else "get"
    return variableNames.filter { PsiNameHelper.getInstance(project).isIdentifier(it) }
      .map { variableName -> "$prefix${StringUtil.capitalize(variableName)}" }
  }

  fun replacePsiRange(source: List<PsiElement>, target: List<PsiElement>): List<PsiElement> {
    if (target.size > 1 && source.first().parent !is PsiCodeBlock) {
      return replacePsiRange(source, wrapWithCodeBlock(target))
    }
    val sourceAsExpression = source.singleOrNull() as? PsiExpression
    val targetAsExpression = target.singleOrNull() as? PsiExpression
    if (sourceAsExpression != null && targetAsExpression != null) {
      val replacedExpression = IntroduceVariableUtil.replace(sourceAsExpression, targetAsExpression, sourceAsExpression.project)
      return listOf(replacedExpression)
    }
    val psiRange = getPhysicalPsiRange(sourceAsExpression) ?: PsiRange(source.first().parent, source.first(), source.last())
    val replacedElements = target.reversed().map { statement -> psiRange.lastChild.addSiblingAfter(statement) }.reversed()
    psiRange.parent.deleteChildRange(psiRange.firstChild, psiRange.lastChild)
    return replacedElements
  }

  fun replaceWithMethod(targetClass: PsiClass, elements: List<PsiElement>, preparedElements: ExtractedElements): ExtractedElements {
    val callElements = preparedElements.callElements
    if (callElements.isNotEmpty()) {
      val addedMethod = ExtractMethodPipeline.addMethodInBestPlace(targetClass, elements.first(), preparedElements.method)
      val replacedElements = replacePsiRange(elements, callElements)
      return ExtractedElements(replacedElements, addedMethod)
    } else {
      val replacedRange = replacePsiRange(elements, listOf(preparedElements.method))
      val replacedMethod = replacedRange.first() as PsiMethod
      return ExtractedElements(emptyList(), replacedMethod)
    }
  }

  fun mergeWriteCommands(editor: Editor, disposable: Disposable, commandName: @Command String) {
    val project = editor.project ?: return
    val startMarkAction = WriteCommandAction.writeCommandAction(project).withName(commandName).compute<StartMarkAction, RuntimeException> {
      StartMarkAction.start(editor, project, commandName)
    }
    Disposer.register(disposable) {
      WriteCommandAction.writeCommandAction(project).withName(commandName).run<RuntimeException > {
        FinishMarkAction.finish(project, editor, startMarkAction)
      }
    }
  }

  private fun getPhysicalPsiRange(expression: PsiExpression?): PsiRange? {
    val range: TextRange = expression?.getUserData(ElementToWorkOn.TEXT_RANGE)?.textRange ?: return null
    val parent: PsiElement = expression.getUserData(ElementToWorkOn.PARENT) ?: return null
    val rangeParent = PsiTreeUtil.findFirstParent(parent) { element -> range in element.textRange } ?: return null
    if (rangeParent.textRange in range) return PsiRange(rangeParent.parent, rangeParent, rangeParent)
    val children = rangeParent.children
    val first = children.firstOrNull { child -> child.textRange in range } ?: return null
    val last = children.lastOrNull { child -> child.textRange in range } ?: return null
    return PsiRange(rangeParent, first, last)
  }

  private data class PsiRange(val parent: PsiElement, val firstChild: PsiElement, val lastChild: PsiElement)

  @RequiresWriteLock
  fun renameTemplate(templateState: TemplateState?, name: String) {
    if (templateState == null) return
    val range = templateState.currentVariableRange ?: return
    templateState.editor.document.replaceString(range.startOffset, range.endOffset, name)
    templateState.update()
  }

  internal suspend fun runWithDumbEditor(editor: Editor, action: suspend () -> Unit) {
    val editorImpl = editor as? EditorImpl
    if (editorImpl == null) {
      action.invoke()
      return
    }
    withContext(Dispatchers.EDT) {
      editorImpl.startDumb()
    }
    try {
      action.invoke()
    }
    finally {
      withContext(Dispatchers.EDT) {
        editor.stopDumbLater()
      }
    }
  }
}