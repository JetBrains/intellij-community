// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.SmartList
import com.siyeh.ig.psiutils.ClassUtils
import com.siyeh.ig.psiutils.ExpressionUtils
import one.util.streamex.MoreCollectors
import one.util.streamex.StreamEx
import org.jetbrains.uast.*
import org.jetbrains.uast.UastBinaryOperator.AssignOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collector

class TaintAnalyzer(private val myTaintValueFactory: TaintValueFactory) {
  private val myVisited: MutableMap<PsiElement, TaintValue> = HashMap()
  private val myVisitedTemporary: MutableMap<PsiElement, TaintValue> = HashMap()
  private val myVisitedMethods: MutableMap<Pair<PsiElement, List<TaintValue>>, TaintValue> = HashMap()
  private val myNonMarkedElements: MutableList<NonMarkedElement> = ArrayList()

  //todo add test for new feature
  private val skipClasses: Set<String> = myTaintValueFactory.getConfiguration()
    .skipClasses
    .filterNotNull()
    .toSet()

  fun analyzeExpression(expression: UExpression, processRecursively: Boolean): TaintValue {
    val file = expression.getContainingUFile() ?: return TaintValue.UNKNOWN
    val context = AnalyzeContext(file, processRecursively, false, 0, 20, true)
    return fromExpressionInner(expression, context)
  }

  private data class AnalyzeContext(val file: UFile,
                                    val collectUsages: Boolean,
                                    val processOnlyConstant: Boolean,
                                    val depthOutside: Int,
                                    val depthInside: Int,
                                    val next: Boolean) {
    fun minusInside(): AnalyzeContext {
      return this.copy(depthInside = depthInside - 1)
    }

    fun minusOutside(): AnalyzeContext {
      return this.copy(processOnlyConstant = true, depthOutside = depthOutside - 1)
    }

    fun notCollect(): AnalyzeContext {
      return this.copy(collectUsages = false)
    }

    fun minusInside(size: Int): AnalyzeContext {
      return this.copy(depthInside = depthInside - size)
    }

    fun notNext(): AnalyzeContext {
      return this.copy(next = false)
    }
  }

  private fun analyzeInner(expression: UExpression, analyzeContext: AnalyzeContext): TaintValue {
    val uResolvable = (expression as? UResolvable) ?: return TaintValue.UNTAINTED
    if (expression.sourcePsi == null) return TaintValue.UNTAINTED
    val sourceTarget = uResolvable.resolve()
    return fromElement(sourceTarget, expression, analyzeContext)
  }

  private fun fromCall(sourceExpression: UExpression, sourceAnalyzeContext: AnalyzeContext): TaintValue? {
    var analyzeContext = sourceAnalyzeContext
    var expression = sourceExpression
    if (analyzeContext.processOnlyConstant) {
      return null
    }
    if (expression is UQualifiedReferenceExpression && expression.selector is UCallExpression) {
      expression = expression.selector
    }
    if (expression !is UCallExpression) {
      return null
    }
    analyzeContext = analyzeContext.minusInside()
    val fromReceiver = fromExpressionWithoutCollection(expression.receiver, analyzeContext)
    val uMethod = expression.resolveToUElement()
    if (uMethod is UMethod && equalFiles(analyzeContext, uMethod) && !uMethod.isConstructor) {
      val jvmModifiersOwner: JvmModifiersOwner = uMethod
      if (fromReceiver == TaintValue.UNTAINTED &&
          (jvmModifiersOwner.hasModifier(JvmModifier.FINAL) || jvmModifiersOwner.hasModifier(JvmModifier.STATIC) ||
           jvmModifiersOwner.hasModifier(JvmModifier.PRIVATE))) {
        val values: MutableList<TaintValue> = mutableListOf()
        analyzeContext = analyzeContext.minusInside(expression.valueArguments.size)
        expression.valueArguments.forEach { argument ->
          values.add(fromExpressionWithoutCollection(argument, analyzeContext))
        }
        return analyzeMethod(uMethod, analyzeContext, values)
      }
      else {
        //only to show tree
        if (analyzeContext.collectUsages) {
          analyzeMethod(uMethod, analyzeContext.notCollect().notNext(), listOf())
        }
        return TaintValue.UNKNOWN
      }
    }
    if (myTaintValueFactory.getConfiguration().processMethodAsQualifierAndArguments) {
      var taintValue = fromReceiver
      expression.valueArguments.forEach { argument ->
        taintValue = taintValue.join(fromExpressionWithoutCollection(argument, analyzeContext.minusInside()))
      }
      return taintValue
    }
    return TaintValue.UNKNOWN
  }

  private fun fromElement(sourcePsiTarget: PsiElement?, expression: UExpression, analyzeContext: AnalyzeContext): TaintValue {
    if (sourcePsiTarget == null) {
      val value = fromCall(expression, analyzeContext)
      return value ?: TaintValue.UNKNOWN
    }
    var value = myVisited[sourcePsiTarget]
    if (value != null) return value
    value = myVisitedTemporary[sourcePsiTarget]
    if (value != null) return value
    var taintValue: TaintValue = myTaintValueFactory.fromAnnotation(sourcePsiTarget) ?: return TaintValue.UNTAINTED
    if (taintValue !== TaintValue.UNKNOWN) return taintValue
    myVisited[sourcePsiTarget] = TaintValue.UNKNOWN
    taintValue = fromModifierListOwner(sourcePsiTarget, expression, analyzeContext) ?: TaintValue.UNTAINTED
    myVisited[sourcePsiTarget] = taintValue
    return taintValue
  }

  val nonMarkedElements: List<NonMarkedElement>
    get() = myNonMarkedElements.toList()

  private fun fromModifierListOwner(sourcePsiTarget: PsiElement,
                                    expression: UExpression,
                                    sourceAnalyzeContext: AnalyzeContext): TaintValue? {
    var analyzeContext = sourceAnalyzeContext
    val owner = (sourcePsiTarget as? PsiModifierListOwner) ?: return null
    analyzeContext = analyzeContext.minusInside()
    if (analyzeContext.depthInside < 0) return null
    var taintValue = fromLocalVar(expression, owner, analyzeContext)
    if (taintValue != null) return taintValue
    taintValue = fromField(owner, analyzeContext)
    if (taintValue == null) {
      taintValue = fromCall(expression, analyzeContext)
    }
    if (taintValue == null) {
      taintValue = fromParam(owner, analyzeContext)
    }
    if (taintValue == null) {
      //it might be kotlin param, for example
      taintValue = fromMethod(owner, analyzeContext)
    }
    if (!analyzeContext.collectUsages && (taintValue == null || taintValue == TaintValue.UNKNOWN)) {
      addByDefault(expression, owner, analyzeContext)
    }
    return taintValue ?: TaintValue.UNKNOWN
  }

  private fun addByDefault(expression: UExpression,
                           owner: PsiModifierListOwner,
                           analyzeContext: AnalyzeContext) {
    val sourcePsi = expression.sourcePsi
    if (sourcePsi != null) {
      myNonMarkedElements.add(NonMarkedElement(owner, sourcePsi, analyzeContext.next))
    }
  }

  private fun fromLocalVar(expression: UExpression, sourceTarget: PsiElement, analyzeContext: AnalyzeContext): TaintValue? {
    if (sourceTarget !is PsiLocalVariable) return null
    val localVariable = (expression as? UResolvable)?.resolveToUElement() as? ULocalVariable ?: return null
    val containingMethod = localVariable.getContainingUMethod() ?: return null
    val checkLocalAfterUsing = possibleToSkipCheckAfterReference(expression, containingMethod)
    return fromVar(sourceTarget, analyzeContext, if (checkLocalAfterUsing) expression else null)
  }

  //Kotlin allows use non-effective-final variables in lambdas
  private fun possibleToSkipCheckAfterReference(expression: UExpression, containingMethod: UMethod): Boolean {
    var checkLocalAfterUsing = true
    var uastParent = expression.uastParent
    while (uastParent != null && uastParent != containingMethod) {
      if (uastParent is ULambdaExpression || uastParent is UAnonymousClass) {
        checkLocalAfterUsing = false
        break
      }
      uastParent = uastParent.uastParent
    }
    return checkLocalAfterUsing
  }

  private fun fromVar(sourceTarget: PsiElement, analyzeContext: AnalyzeContext, usedReference: UExpression? = null): TaintValue? {
    val psiVariable = (sourceTarget as? PsiVariable) ?: return null
    val uVariable = psiVariable.toUElement(UVariable::class.java) ?: return null
    val uInitializer = uVariable.uastInitializer
    val taintValue = fromExpressionWithoutCollection(uInitializer, analyzeContext)
    if (taintValue == TaintValue.TAINTED) return taintValue
    var codeBlock: UElement? = null
    if (uVariable is ULocalVariable) {
      codeBlock = uVariable.getParentOfType(UBlockExpression::class.java)
    }
    else if (uVariable is UField) {
      codeBlock = uVariable.uastParent
    }
    return codeBlock?.let { analyzeVar(taintValue, it, psiVariable, analyzeContext, usedReference) } ?: taintValue
  }

  private fun analyzeVar(taintValue: TaintValue,
                         codeBlock: UElement,
                         psiVariable: PsiVariable,
                         analyzeContext: AnalyzeContext,
                         usedReference: UExpression? = null): TaintValue {

    class VarAnalyzer(var myTaintValue: TaintValue) : AbstractUastVisitor() {

      private var stopAnalysis = false
      override fun visitBlockExpression(node: UBlockExpression): Boolean {
        for (expression in node.expressions) {
          expression.accept(this)
          if (stopAnalysis) return true
          if (myTaintValue == TaintValue.TAINTED) return true
        }
        return true
      }

      override fun visitExpression(node: UExpression): Boolean {
        if (usedReference == node) {
          stopAnalysis = true
          return true
        }
        return super.visitExpression(node)
      }

      override fun visitCallExpression(node: UCallExpression): Boolean {
        val receiver = node.receiver
        if (receiver != null) {
          myTaintValue = myTaintValue.join(checkUsages(listOf(receiver)))
        }
        myTaintValue = myTaintValue.join(checkUsages(node.valueArguments))
        return if (myTaintValue == TaintValue.TAINTED) true else super.visitCallExpression(node)
      }

      private fun checkUsages(expressions: List<UExpression?>): TaintValue {
        if (skipClass(psiVariable.type)) {
          return TaintValue.UNTAINTED
        }
        if (ClassUtils.isImmutable(psiVariable.type)) {
          return TaintValue.UNTAINTED
        }
        for (expression in expressions) {
          if (expression == null) {
            continue
          }
          val hasUsage = AtomicBoolean()
          expression.accept(object : AbstractUastVisitor() {
            override fun visitExpression(node: UExpression): Boolean {
              if (node is UReferenceExpression) {
                if (node.resolve() == psiVariable) {
                  hasUsage.set(true)
                }
                return true
              }
              return super.visitExpression(node)
            }
          })
          if (hasUsage.get()) {
            return TaintValue.UNKNOWN
          }
        }
        return TaintValue.UNTAINTED
      }

      override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
        if (node.operator !is AssignOperator) return super.visitBinaryExpression(node)
        val lhs = (node.leftOperand as? UReferenceExpression)
        if (lhs == null || psiVariable != lhs.resolve()) return super.visitBinaryExpression(node)
        val rhs = node.rightOperand
        myTaintValue = myTaintValue.join(fromExpressionWithoutCollection(rhs, analyzeContext))
        return if (myTaintValue == TaintValue.TAINTED) true else super.visitBinaryExpression(node)
      }
    }

    val varAnalyzer = VarAnalyzer(taintValue)
    codeBlock.accept(varAnalyzer)
    return varAnalyzer.myTaintValue
  }

  private fun fromParam(target: PsiElement?, analyzeContext: AnalyzeContext): TaintValue? {
    val psiParameter = (target as? PsiParameter) ?: return null
    val uParameter = target.toUElement(UParameter::class.java) ?: return null
    // default parameter value
    val uInitializer = uParameter.uastInitializer
    var taintValue = fromExpressionWithoutCollection(uInitializer, analyzeContext.minusInside())
    if (taintValue == TaintValue.TAINTED) return taintValue
    val uMethod = (uParameter.uastParent as? UMethod) ?: return TaintValue.UNTAINTED
    val uBlock = (uMethod.uastBody as? UBlockExpression)
    if (uBlock != null) taintValue = analyzeVar(taintValue, uBlock, psiParameter, analyzeContext)
    if (taintValue == TaintValue.TAINTED) return taintValue
    val nonMarkedElements = SmartList<NonMarkedElement?>()
    // this might happen when we analyze kotlin primary constructor parameter
    if (uBlock == null && analyzeContext.collectUsages) nonMarkedElements.addAll(findAssignments(psiParameter))
    val psiMethod = (uMethod.sourcePsi as? PsiMethod)
    if (psiMethod != null && analyzeContext.collectUsages) {
      val paramIdx = uMethod.uastParameters.indexOf(uParameter)
      nonMarkedElements.addAll(findArgs(psiMethod, paramIdx))
    }
    myNonMarkedElements.addAll(nonMarkedElements.filterNotNull())
    return TaintValue.UNKNOWN
  }

  private fun fromField(target: PsiElement, analyzeContext: AnalyzeContext): TaintValue? {
    if (analyzeContext.depthOutside < 0) return null
    val uElement = target.toUElement() as? UField ?: return null
    val sourcePsi = uElement.sourcePsi ?: return null
    val jvmModifiersOwner: JvmModifiersOwner = uElement
    if (jvmModifiersOwner.hasModifier(JvmModifier.FINAL) || (jvmModifiersOwner.hasModifier(
        JvmModifier.PRIVATE) && canFieldAssignOnlyInConstructors(
        target))) {
      val result: TaintValue? = if (!equalFiles(analyzeContext, uElement)) {
        fromVar(sourcePsi, analyzeContext.minusOutside().notCollect())
      }
      else {
        fromVar(sourcePsi, analyzeContext.notCollect())
      }

      if (result != null) {
        return result
      }
      return if (jvmModifiersOwner.hasModifier(JvmModifier.FINAL)) {
        TaintValue.UNTAINTED
      }
      else {
        TaintValue.UNKNOWN
      }
    }
    if (analyzeContext.processOnlyConstant) {
      return null
    }
    if (analyzeContext.collectUsages) {
      val children: MutableList<NonMarkedElement?> = ArrayList()
      val initializer = NonMarkedElement.create(uElement.uastInitializer, analyzeContext.next)
      if (initializer != null) children.add(initializer)
      children.addAll(findAssignments(target))
      myNonMarkedElements.addAll(children.filterNotNull())
    }
    return TaintValue.UNKNOWN
  }

  private fun fromMethod(target: PsiElement, analyzeContext: AnalyzeContext): TaintValue? {
    if (analyzeContext.processOnlyConstant) {
      return null
    }
    val uMethod = target.toUElement(UMethod::class.java) ?: return null
    val values: MutableList<TaintValue> = ArrayList()
    val parameters = uMethod.uastParameters
    for (i in parameters.indices) {
      values.add(TaintValue.UNKNOWN)
    }
    return analyzeMethod(uMethod, analyzeContext, values)
  }

  private fun analyzeMethod(uMethod: UMethod, analyzeContext: AnalyzeContext, arguments: List<TaintValue>): TaintValue {
    if (!equalFiles(analyzeContext, uMethod)) return TaintValue.UNKNOWN
    val psiElement = uMethod.sourcePsi ?: return TaintValue.UNKNOWN
    val key = Pair(psiElement, arguments)
    val value = myVisitedMethods[key]
    if (value != null) return value
    class MethodAnalyzer : AbstractUastVisitor() {
      var myTaintValue = TaintValue.UNTAINTED
      override fun visitBlockExpression(node: UBlockExpression): Boolean {
        for (expression in node.expressions) {
          expression.accept(this)
        }
        return true
      }

      override fun visitReturnExpression(node: UReturnExpression): Boolean {
        val returnExpression = node.returnExpression ?: return true
        myTaintValue = myTaintValue.join(fromExpressionWithoutCollection(returnExpression, analyzeContext.minusInside()))
        return if (myTaintValue == TaintValue.TAINTED) true else super.visitReturnExpression(node)
      }
    }

    val methodBody = (uMethod.uastBody as? UBlockExpression)
    if (methodBody == null) {
      // maybe it is a generated kotlin property getter or setter
      val sourcePsi = uMethod.sourcePsi ?: return TaintValue.UNTAINTED
      val taintValue = fromField(sourcePsi, analyzeContext)
      return taintValue ?: TaintValue.UNTAINTED
    }
    val methodAnalyzer = MethodAnalyzer()
    myVisitedMethods[key] = TaintValue.UNKNOWN
    val previous = HashMap(myVisitedTemporary)
    if (!arguments.isEmpty()) {
      val parameters = uMethod.uastParameters
      for (i in parameters.indices) {
        val sourcePsi = parameters[i].sourcePsi ?: continue
        myVisitedTemporary[sourcePsi] = if (arguments.size <= i) arguments[arguments.size - 1] else arguments[i]
      }
    }
    methodBody.accept(methodAnalyzer)
    val returnValue = methodAnalyzer.myTaintValue
    myVisitedTemporary.clear()
    myVisitedTemporary.putAll(previous)
    myVisitedMethods[key] = returnValue
    return returnValue
  }

  private fun fromExpressionWithoutCollection(uExpression: UExpression?, sourceAnalyzeContext: AnalyzeContext): TaintValue {
    var analyzeContext = sourceAnalyzeContext
    analyzeContext = analyzeContext.notCollect()
    return fromExpressionInner(uExpression, analyzeContext.notCollect())
  }

  private fun fromExpressionInner(sourceUExpression: UExpression?, analyzeContext: AnalyzeContext): TaintValue {
    var uExpression = sourceUExpression ?: return TaintValue.UNTAINTED
    if (analyzeContext.depthInside < 0) return TaintValue.UNKNOWN
    val type = uExpression.getExpressionType()
    if (type != null && skipClass(type)) return TaintValue.UNTAINTED
    if (uExpression is UThisExpression) return TaintValue.UNTAINTED
    uExpression = uExpression.skipParenthesizedExprDown()
    if (uExpression is ULiteralExpression) return TaintValue.UNTAINTED
    if (uExpression is UResolvable) return analyzeInner(uExpression, analyzeContext)
    val uConcatenation = getConcatenation(uExpression)
    if (uConcatenation != null) {
      val size = uConcatenation.operands.size
      return StreamEx.of(uConcatenation.operands).collect(joining(analyzeContext.minusInside(size)))
    }
    val uIfExpression = (uExpression as? UIfExpression)
    if (uIfExpression != null) {
      return StreamEx.of(uIfExpression.thenExpression, uIfExpression.elseExpression)
        .collect(joining(analyzeContext.minusInside(2)))
    }
    val switchExpression = (uExpression as? USwitchExpression)
    if (switchExpression != null) {
      val size = switchExpression.body.expressions.size
      return StreamEx.of(switchExpression.body.expressions)
        .collect(joining(analyzeContext.minusInside(size)))
    }
    if (uExpression is ULambdaExpression) return TaintValue.UNKNOWN
    val javaPsi = (uExpression.javaPsi as? PsiExpression) ?: return TaintValue.UNTAINTED
    val list = ExpressionUtils.nonStructuralChildren(javaPsi).toList()
    return StreamEx.of(list)
      .map { e: PsiExpression -> e.toUElement(UExpression::class.java) }
      .collect(joining(analyzeContext.minusInside(list.size)))
  }

  private fun skipClass(type: PsiType): Boolean {
    val aClass = PsiUtil.resolveClassInClassTypeOnly(type)
    return skipClasses.contains(type.canonicalText) ||
           skipClasses.any { cl: String? ->
             cl != null && InheritanceUtil.isInheritor(type, cl)
           } ||
           (aClass != null && (aClass.isInterface || aClass.isEnum))
  }

  private fun joining(analyzeContext: AnalyzeContext): Collector<UExpression?, *, TaintValue> {
    return MoreCollectors.mapping(
      { e: UExpression? -> fromExpressionWithoutCollection(e, analyzeContext) }, TaintValue.joining())
  }

  companion object {
    private fun equalFiles(analyzeContext: AnalyzeContext, method: UElement): Boolean {
      val file = method.getContainingUFile()
      return file != null && analyzeContext.file.sourcePsi == file.sourcePsi
    }

    private fun canFieldAssignOnlyInConstructors(target: PsiElement): Boolean {
      val uElement = target.toUElement()
      if (uElement == null) return false
      val containingUClass = uElement.getContainingUClass() ?: return false

      class FieldAssignmentAnalyzer : AbstractUastVisitor() {
        var assignOutsideConstructor = false
        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
          if (node.operator !is AssignOperator) return super.visitBinaryExpression(node)
          val lhs = (node.leftOperand as? UReferenceExpression)
          if (lhs == null || target != lhs.resolve()) return super.visitBinaryExpression(node)
          val containingUMethod = node.getContainingUMethod() ?: return super.visitBinaryExpression(node)
          if (!containingUMethod.isConstructor) {
            assignOutsideConstructor = true
            return true
          }
          return super.visitBinaryExpression(node)
        }
      }

      return containingUClass.methods.none {
        val visitor = FieldAssignmentAnalyzer()
        it.accept(visitor)
        !it.isConstructor && visitor.assignOutsideConstructor
      }
    }

    private fun findArgs(psiMethod: PsiMethod, paramIdx: Int): Collection<NonMarkedElement?> {
      return ReferencesSearch.search(psiMethod, psiMethod.useScope)
        .mapping { it.element.parent as? PsiMethodCallExpression }
        .mapping { it?.argumentList?.expressions }
        .filtering { args -> args != null && args.size > paramIdx }
        .mapping { args -> args[paramIdx].toUElement() }
        .mapping { arg -> NonMarkedElement.create(arg, false) }
        .filtering { arg -> arg != null }
        .findAll()
    }

    private fun findAssignments(target: PsiElement): Collection<NonMarkedElement?> {
      return ReferencesSearch.search(target, target.useScope)
        .mapping { u: PsiReference ->
          u.element.getUastParentOfType(
            UBinaryExpression::class.java)
        }
        .filtering { binary: UBinaryExpression? -> isLhs(binary, target) }
        .mapping { binary: UBinaryExpression? -> NonMarkedElement.create(binary?.rightOperand, false) }
        .filtering { e: NonMarkedElement? -> e != null }
        .findAll()
    }

    private fun isLhs(uBinary: UBinaryExpression?, target: PsiElement): Boolean {
      if (uBinary == null) return false
      val operator = uBinary.operator
      if (operator !== UastBinaryOperator.ASSIGN && operator !== UastBinaryOperator.PLUS_ASSIGN) return false
      val leftOperand = (uBinary.leftOperand.skipParenthesizedExprDown() as? UResolvable) ?: return false
      val lhsTarget = leftOperand.resolve()
      if (lhsTarget == target) return true
      // maybe it's kotlin property auto generated setter
      if (lhsTarget !is PsiMethod || lhsTarget.body != null) {
        return false
      }
      val uElement = lhsTarget.toUElement() ?: return false
      val property = uElement.sourcePsi
      return property == target
    }

    private fun getConcatenation(uExpression: UExpression): UPolyadicExpression? {
      val uPolyadic = (uExpression as? UPolyadicExpression) ?: return null
      val uOperator = uPolyadic.operator
      return if (uOperator == UastBinaryOperator.PLUS || uOperator == UastBinaryOperator.PLUS_ASSIGN) uPolyadic else null
    }
  }
}
