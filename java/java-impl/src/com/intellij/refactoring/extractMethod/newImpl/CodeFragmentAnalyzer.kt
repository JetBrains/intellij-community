// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.ExceptionUtil
import com.intellij.codeInsight.Nullability
import com.intellij.codeInspection.dataFlow.*
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.controlFlow.*
import com.intellij.psi.controlFlow.ControlFlow
import com.intellij.psi.controlFlow.ControlFlowUtil.DEFAULT_EXIT_STATEMENTS_CLASSES
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor
import com.siyeh.ig.psiutils.VariableAccessUtils
import it.unimi.dsi.fastutil.ints.IntArrayList

data class ExitDescription(val statements: List<PsiStatement>, val numberOfExits: Int, val hasSpecialExits: Boolean)
data class ExternalReference(val variable: PsiVariable, val references: List<PsiReferenceExpression>)
data class FieldUsage(val field: PsiField, val classMemberReference: PsiReferenceExpression, val isWrite: Boolean)

class CodeFragmentAnalyzer(val elements: List<PsiElement>) {

  init {
    require(elements.isNotEmpty())
  }

  private val codeFragment = ControlFlowUtil.findCodeFragment(elements.first())
  private val flow: ControlFlow = createControlFlow(elements)
  private val flowRange = findFlowRange(flow, elements)

  private fun createControlFlow(elements: List<PsiElement>): ControlFlow {
    try {
      val fragmentToAnalyze: PsiElement = codeFragment
      val flowPolicy = LocalsControlFlowPolicy(fragmentToAnalyze)
      val factory: ControlFlowFactory = ControlFlowFactory.getInstance(elements.first().project)
      return factory.getControlFlow(fragmentToAnalyze, flowPolicy, false, false)
    } catch (e: AnalysisCanceledException) {
      throw ExtractException(JavaRefactoringBundle.message("extract.method.control.flow.analysis.failed"), e.errorElement)
    }
  }

  private fun findFlowRange(flow: ControlFlow, elements: List<PsiElement>): IntRange {
    val expression = elements.singleOrNull() as? PsiParenthesizedExpression
    val normalizedElements = if (expression != null) listOfNotNull(PsiUtil.skipParenthesizedExprDown(expression)) else elements
    val firstElementInFlow = normalizedElements.find { element -> flow.getStartOffset(element) >= 0 }
    val lastElementInFlow = normalizedElements.findLast { element -> flow.getEndOffset(element) >= 0 }
    requireNotNull(firstElementInFlow)
    requireNotNull(lastElementInFlow)
    return flow.getStartOffset(firstElementInFlow)..flow.getEndOffset(lastElementInFlow)
  }

  private fun findVariableReferences(variable: PsiVariable): List<PsiReferenceExpression> {
    return elements.flatMap { VariableAccessUtils.getVariableReferences(variable, it) }
  }

  fun findExternalReferences(): List<ExternalReference> {
    return ControlFlowUtil.getInputVariables(flow, flowRange.first, flowRange.last)
      .filterNot { variable -> variable in this }
      .sortedWith( Comparator { v1: PsiVariable, v2: PsiVariable -> when {
          v1.type is PsiEllipsisType -> 1
          v2.type is PsiEllipsisType -> -1
          else -> v1.textOffset - v2.textOffset
      }})
      .map { variable -> ExternalReference(variable, findVariableReferences(variable)) }
  }

  fun findUsedVariablesAfter(): List<PsiVariable> {
    return ControlFlowUtil.getUsedVariables(flow, flowRange.last, flow.size)
  }

  fun findOuterLocals(sourceClassMember: PsiElement, targetClassMember: PsiElement): List<ExternalReference>? {
    val outerVariables = mutableListOf<PsiVariable>()
    val canBeExtracted = elements
      .all { element -> ControlFlowUtil.collectOuterLocals(outerVariables, element, sourceClassMember, targetClassMember) }
    if (!canBeExtracted) return null
    return outerVariables.map { variable -> ExternalReference(variable, findVariableReferences(variable)) }
  }

  fun findOutputVariables(): List<PsiVariable> {
    val exitPoints = IntArrayList()
    ControlFlowUtil.findExitPointsAndStatements(flow, flowRange.first, flowRange.last, exitPoints, *DEFAULT_EXIT_STATEMENTS_CLASSES)
    return ControlFlowUtil.getOutputVariables(flow, flowRange.first, flowRange.last, exitPoints.toIntArray()).distinct()
  }

  fun findUndeclaredVariables(): List<PsiVariable> {
    return ControlFlowUtil
      .getWrittenVariables(flow, flowRange.first, flowRange.last, false)
      .filterNot { variable ->
        variable.textRange in TextRange(elements.first().textRange.startOffset, elements.last().textRange.endOffset)
      }
  }

  fun hasObservableThrowExit(): Boolean {
    return ControlFlowUtil.hasObservableThrowExitPoints(flow, flowRange.first, flowRange.last, elements.toTypedArray(), codeFragment)
  }

  fun findExitDescription(): ExitDescription {
    val statements = ControlFlowUtil
      .findExitPointsAndStatements(flow, flowRange.first, flowRange.last, IntArrayList(), *DEFAULT_EXIT_STATEMENTS_CLASSES)
      .filterNot { statement -> isExitInside(statement) }
    val exitPoints = findExitPoints()
    val hasSpecialExits = exitPoints.singleOrNull() != lastGotoPointFrom(flowRange.last)
    return ExitDescription(statements, maxOf(1, exitPoints.size), hasSpecialExits)
  }

  fun findExposedLocalDeclarations(): List<PsiVariable> {
    val declaredVariables = HashSet<PsiVariable>()
    val visitor = object : JavaRecursiveElementWalkingVisitor() {
      override fun visitDeclarationStatement(statement: PsiDeclarationStatement) {
        declaredVariables += statement.declaredElements.filterIsInstance<PsiVariable>()
      }
    }
    elements.forEach { it.accept(visitor) }
    val externallyWrittenVariables = ControlFlowUtil.getWrittenVariables(flow, flowRange.last, flow.size, false).toSet()
    return declaredVariables.intersect(externallyWrittenVariables).toList()
  }

  fun findFieldUsages(targetClass: PsiClass, elements: List<PsiElement>): List<FieldUsage> {
    val usedFields = ArrayList<FieldUsage>()
    val visitor = object : ClassMemberReferencesVisitor(targetClass) {
      override fun visitClassMemberReferenceElement(classMember: PsiMember, classMemberReference: PsiJavaCodeReferenceElement) {
        val expression = PsiTreeUtil.getParentOfType(classMemberReference, PsiExpression::class.java, false)
        if (classMember is PsiField && expression != null && classMemberReference is PsiReferenceExpression) {
          usedFields += FieldUsage(classMember, classMemberReference, PsiUtil.isAccessedForWriting(expression))
        }
      }
    }
    elements.forEach { it.accept(visitor) }
    return usedFields.distinct()
  }

  private fun lastGotoPointFrom(instructionOffset: Int): Int {
    if (instructionOffset >= flow.size) return instructionOffset
    val instruction = flow.instructions[instructionOffset]
    fun returnsValue(instructionOffset: Int): Boolean = (flow.getElement(instructionOffset) as? PsiReturnStatement)?.returnValue != null
    return if (instruction is GoToInstruction && !returnsValue(instructionOffset)) {
      lastGotoPointFrom(instruction.offset)
    } else {
      instructionOffset
    }
  }

  private fun isNonLocalJump(instructionOffset: Int): Boolean {
    val instruction = flow.instructions[instructionOffset]
    return when (instruction) {
      is ThrowToInstruction, is ConditionalThrowToInstruction, is ReturnInstruction -> false
      is GoToInstruction -> instruction.offset !in (flowRange.first until flowRange.last)
      is BranchingInstruction -> instruction.offset !in (flowRange.first until flowRange.last)
      else -> false
    }
  }

  private fun isInstructionReachable(offset: Int): Boolean {
    return offset == flowRange.first || ControlFlowUtil.isInstructionReachable(flow, offset, flowRange.first)
  }

  private fun findDefaultExits(): List<Int> {
    val lastInstruction = flow.instructions[flowRange.last - 1]
    if (isInstructionReachable(flowRange.last - 1)) {
      val defaultExits = when (lastInstruction) {
        is ThrowToInstruction -> emptyList()
        is GoToInstruction -> listOf(lastInstruction.offset)
        is ConditionalThrowToInstruction -> listOf(flowRange.last)
        is BranchingInstruction -> listOf(lastInstruction.offset, flowRange.last)
        else -> listOf(flowRange.last)
      }
      return defaultExits.filterNot { it in flowRange.first until flowRange.last }
    }
    else {
      return emptyList()
    }
  }

  private fun findExitPoints(): List<Int> {
    if (flowRange.first == flowRange.last) return listOf(flowRange.last)

    val gotoInstructions = (flowRange.first until flowRange.last)
      .asSequence()
      .filter { offset -> isNonLocalJump(offset) }
      .distinctBy { offset -> (flow.instructions[offset] as BranchingInstruction).offset }
      .filter { offset -> isInstructionReachable(offset) }
      .toList()

    val jumpPoints = gotoInstructions
      .map { offset -> (flow.instructions[offset] as BranchingInstruction).offset }
      .toSet()

    val allExitPoints = jumpPoints + findDefaultExits()
    return allExitPoints.map { lastGotoPointFrom(it) }.distinct()
  }

  fun findThrownExceptions(): List<PsiClassType> {
    return ExceptionUtil.getThrownCheckedExceptions(*elements.toTypedArray())
  }

  fun findExposedLocalVariables(expressions: List<PsiExpression>): List<PsiVariable> {
    val exposedLocalVariables = HashSet<PsiVariable>()
    val visitor = object : JavaRecursiveElementWalkingVisitor() {

      override fun visitReferenceExpression(reference: PsiReferenceExpression) {
        val variable = reference.resolve() as? PsiVariable ?: return
        if (variable.textRange in TextRange(elements.first().textRange.startOffset, elements.last().textRange.endOffset)) {
          exposedLocalVariables += variable
        }
      }
    }
    expressions.forEach { it.accept(visitor) }

    return exposedLocalVariables.toList()
  }

  fun findWrittenVariables(): List<PsiVariable> {
    return ControlFlowUtil.getWrittenVariables(flow, flowRange.first, flowRange.last, false).toList()
  }

  private fun isExitInside(statement: PsiStatement): Boolean {
    return when (statement) {
      is PsiBreakStatement -> contains(statement.findExitedStatement())
      is PsiContinueStatement -> contains(statement.findContinuedStatement())
      is PsiReturnStatement -> false
      else -> false
    }
  }

  operator fun contains(element: PsiElement?): Boolean {
    if (element == null) return false
    val textRange = TextRange(elements.first().textRange.startOffset, elements.last().textRange.endOffset)
    return element.textRange in textRange
  }

  companion object {
    fun inferNullability(expressionGroup: List<PsiExpression>): Nullability {
      if (expressionGroup.any { it.type == PsiType.NULL }) return Nullability.NULLABLE

      if (expressionGroup.isEmpty()) return Nullability.UNKNOWN
      val fragmentToAnalyze = ControlFlowUtil.findCodeFragment(expressionGroup.first())
      val dfaRunner = DataFlowRunner(fragmentToAnalyze.project)

      var nullability = DfaNullability.NOT_NULL

      class Visitor : StandardInstructionVisitor() {
        override fun beforeExpressionPush(value: DfaValue, expr: PsiExpression, range: TextRange?, state: DfaMemoryState) {
          if (expr in expressionGroup) {
            val expressionNullability = when {
              state.isNotNull(value) -> DfaNullability.NOT_NULL
              state.isNull(value) -> DfaNullability.NULL
              else -> DfaNullability.fromDfType(state.getDfType(value))
            }
            nullability = nullability.unite(expressionNullability)
          }
        }
      }

      val visitor = Visitor()
      val runnerState = dfaRunner.analyzeMethod(fragmentToAnalyze, visitor)
      return if (runnerState == RunnerResult.OK) {
        DfaNullability.toNullability(nullability)
      } else {
        Nullability.UNKNOWN
      }
    }

    fun inferNullability(place: PsiElement, probeExpression: String?): Nullability {
      if (probeExpression == null) return Nullability.UNKNOWN
      val factory = PsiElementFactory.getInstance(place.project)
      val sourceClass = findClassMember(place)?.containingClass ?: return Nullability.UNKNOWN
      val copyFile = sourceClass.containingFile.copy() as PsiFile
      val copyPlace = PsiTreeUtil.findSameElementInCopy(place, copyFile)
      val probeStatement = factory.createStatementFromText("return $probeExpression;", null)

      val parent = copyPlace.parent
      val codeBlock = if (parent is PsiCodeBlock) {
        copyPlace.parent as PsiCodeBlock
      } else {
        val block = copyPlace.parent.replace(factory.createCodeBlock()) as PsiCodeBlock
        block.add(copyPlace)
        block
      }
      val artificialReturn = codeBlock.add(probeStatement) as PsiReturnStatement
      val artificialExpression = requireNotNull(artificialReturn.returnValue)
      return inferNullability(listOf(artificialExpression))
    }

    fun findReturnExpressionsIn(scope: PsiElement): List<PsiExpression> {
      val expressions = mutableListOf<PsiExpression>()
      val visitor: JavaRecursiveElementWalkingVisitor = object : JavaRecursiveElementWalkingVisitor() {
        override fun visitReturnStatement(statement: PsiReturnStatement) {
          val returnExpression = statement.returnValue
          if (returnExpression != null) expressions += returnExpression
        }
      }
      scope.accept(visitor)
      return expressions
    }
  }
}