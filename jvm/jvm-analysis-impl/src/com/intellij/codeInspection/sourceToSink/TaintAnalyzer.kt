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
import one.util.streamex.MoreCollectors
import one.util.streamex.StreamEx
import org.jetbrains.uast.*
import org.jetbrains.uast.UastBinaryOperator.AssignOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collector

class TaintAnalyzer(private val myTaintValueFactory: TaintValueFactory) {
  private val myVisited: MutableMap<PsiElement, TaintValue> = HashMap()
  private val myVisitedTemporary: MutableMap<PsiElement, TaintValue> = HashMap()
  private val myVisitedMethods: MutableMap<Pair<PsiElement, List<TaintValue>>, TaintValue> = HashMap()
  private val myNonMarkedElements: MutableList<NonMarkedElement> = ArrayList()

  private val skipClasses: Set<String> = myTaintValueFactory.getConfiguration()
    .skipClasses
    .filterNotNull()
    .toSet()

  @Throws(DeepTaintAnalyzerException::class)
  fun analyzeExpression(expression: UExpression, processRecursively: Boolean): TaintValue {
    val file = expression.getContainingUFile() ?: return TaintValue.UNKNOWN
    val context = AnalyzeContext.create(file, processRecursively, false, 1, 30, 2, true)
    return fromExpressionInner(expression, context)
  }

  private data class AnalyzeContext(val file: UFile,
                                    val collectUsages: Boolean,
                                    val processOnlyConstant: Boolean,
                                    val depthOutside: Int,
                                    private val depthInside: AtomicInteger,
                                    private val depthNestedMethods: Int,
                                    val next: Boolean) {
    companion object {
      fun create(file: UFile,
                 collectUsages: Boolean,
                 processOnlyConstant: Boolean,
                 depthOutside: Int,
                 depthInside: Int,
                 depthNestedMethods: Int,
                 next: Boolean): AnalyzeContext {
        return AnalyzeContext(file, collectUsages, processOnlyConstant, depthOutside, AtomicInteger(depthInside),
                              depthNestedMethods, next)
      }
    }

    fun minusMethod(): AnalyzeContext {
      val depth = depthNestedMethods - 1
      if (depth < 0) {
        throw DeepTaintAnalyzerException()
      }
      return copy(depthNestedMethods = depth)
    }

    fun minusInside(): AnalyzeContext {
      val depth = depthInside.decrementAndGet()
      if (depth < 0) {
        throw DeepTaintAnalyzerException()
      }
      return this
    }

    fun minusInside(size: Int): AnalyzeContext {
      val previous = depthInside.get()
      val next = previous - size
      depthInside.set(next)
      if (next < 0) {
        throw DeepTaintAnalyzerException()
      }
      return this
    }

    fun notCollect(): AnalyzeContext {
      if (collectUsages) {
        return this.copy(collectUsages = false)
      }
      return this
    }

    fun minusOutside(): AnalyzeContext {
      return this.copy(processOnlyConstant = true, depthOutside = depthOutside - 1)
    }

    fun notNext(): AnalyzeContext {
      if (next) {
        return this.copy(next = false)
      }
      return this
    }

    fun checkInside(size: Int) {
      if (depthInside.get() - size < 0) {
        throw DeepTaintAnalyzerException()
      }
    }
  }

  private fun analyzeInner(expression: UExpression, analyzeContext: AnalyzeContext): TaintValue {
    val uResolvable = (expression as? UResolvable) ?: return TaintValue.UNTAINTED
    if (expression.sourcePsi == null) return TaintValue.UNTAINTED
    val sourceTarget = uResolvable.resolve()
    if (sourceTarget == null) {
      return fromCall(expression, analyzeContext) ?: TaintValue.UNKNOWN
    }
    val resolvedUElement = uResolvable.resolveToUElement()
    val sourcePsi = resolvedUElement?.sourcePsi
    if (sourcePsi != null) {
      val previousValue = myVisitedTemporary[sourcePsi]
      if (previousValue != null) {
        return previousValue
      }
    }
    var taintValue: TaintValue = myTaintValueFactory.fromElement(sourceTarget) ?: return TaintValue.UNTAINTED
    if (taintValue !== TaintValue.UNKNOWN) return taintValue
    val value = checkAndPrepareVisited(resolvedUElement)
    if (value != null) return value
    taintValue = fromModifierListOwner(sourceTarget, expression, analyzeContext) ?: TaintValue.UNTAINTED
    addToVisited(resolvedUElement, taintValue)
    return taintValue
  }

  private fun checkAndPrepareVisited(uElement: UElement?, prepare: Boolean = true): TaintValue? {
    if (uElement == null) return null
    val sourcePsi = uElement.sourcePsi
    if (sourcePsi != null) {
      val value = myVisited[sourcePsi]
      if (value != null) {
        return value
      }
      if (prepare) {
        addToVisited(uElement, TaintValue.UNKNOWN)
      }
    }
    return null
  }

  private fun addToVisited(uElement: UElement?, result: TaintValue?) {
    val sourcePsi = uElement?.sourcePsi
    if (sourcePsi == null) return
    if (result == null) {
      myVisited.remove(sourcePsi)
      return
    }
    if (uElement is UVariable) {
      myVisited[sourcePsi] = result
    }
  }

  private fun fromCall(sourceExpression: UExpression, analyzeContext: AnalyzeContext): TaintValue? {
    var expression = sourceExpression
    if (analyzeContext.processOnlyConstant) {
      return null
    }

    //fields as methods
    if (expression is UQualifiedReferenceExpression && expression.selector is UReferenceExpression) {
      val uMethod = expression.resolveToUElement()
      if (uMethod is UMethod) {
        val equalFiles = equalFiles(analyzeContext, uMethod)
        if (myTaintValueFactory.getConfiguration().processMethodAsQualifierAndArguments && !equalFiles) {
          val fromReceiver = fromExpressionWithoutCollection(expression.receiver, analyzeContext)
          if (fromReceiver == TaintValue.UNTAINTED && uMethod.uastParameters.isEmpty()) {
            return fromReceiver
          }
        }
      }
    }

    if (expression is UQualifiedReferenceExpression && expression.selector is UCallExpression) {
      expression = expression.selector
    }
    if (expression is UArrayAccessExpression) {
      analyzeContext.checkInside(1 + expression.indices.size)
      var result = fromExpressionWithoutCollection(expression.receiver, analyzeContext)
      expression.indices.forEach { result = result.join(fromExpressionWithoutCollection(it, analyzeContext)) }
      return result
    }
    if (expression !is UCallExpression) {
      return null
    }

    val fromReceiver = fromExpressionWithoutCollection(expression.receiver, analyzeContext)
    val uMethod = expression.resolveToUElement()
    if (uMethod is UMethod && equalFiles(analyzeContext, uMethod) && !uMethod.isConstructor) {
      val jvmModifiersOwner: JvmModifiersOwner = uMethod
      if (fromReceiver == TaintValue.UNTAINTED &&
          (jvmModifiersOwner.hasModifier(JvmModifier.FINAL) || jvmModifiersOwner.hasModifier(JvmModifier.STATIC) ||
           jvmModifiersOwner.hasModifier(JvmModifier.PRIVATE))) {
        val values: MutableList<TaintValue> = mutableListOf()
        analyzeContext.checkInside(expression.valueArguments.size)
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
      analyzeContext.checkInside(expression.valueArguments.size)
      expression.valueArguments.forEach { argument ->
        taintValue = taintValue.join(fromExpressionWithoutCollection(argument, analyzeContext))
      }
      return taintValue
    }
    return TaintValue.UNKNOWN
  }

  val nonMarkedElements: List<NonMarkedElement>
    get() = myNonMarkedElements.distinct().toList()

  private fun fromModifierListOwner(sourcePsiTarget: PsiElement,
                                    expression: UExpression,
                                    analyzeContext: AnalyzeContext): TaintValue? {

    val owner = (sourcePsiTarget as? PsiModifierListOwner) ?: return null
    var taintValue = fromLocalVar(expression, owner, analyzeContext)
    if (taintValue != null) {
      return taintValue
    }
    taintValue = fromField(expression, owner, analyzeContext)
    if (taintValue == null) {
      taintValue = fromCall(expression, analyzeContext)
    }
    if (taintValue == null) {
      taintValue = fromParam(expression, owner, analyzeContext)
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
    val skipAfterReference = possibleToSkipCheckAfterReference(expression, containingMethod)
    return fromVar(sourceTarget, analyzeContext, expression, skipAfterReference)
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

  private fun fromVar(sourceTarget: PsiElement,
                      analyzeContext: AnalyzeContext,
                      usedReference: UExpression?,
                      skipAfterReference: Boolean): TaintValue? {
    val uVariable = sourceTarget.toUElement(UVariable::class.java) ?: return null
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
    return codeBlock?.let { analyzeVar(taintValue, it, uVariable, analyzeContext, usedReference, skipAfterReference) } ?: taintValue
  }

  private fun analyzeVar(taintValue: TaintValue,
                         codeBlock: UElement,
                         uVariable: UVariable,
                         analyzeContext: AnalyzeContext,
                         usedReference: UExpression?,
                         skipAfterReference: Boolean): TaintValue {

    class VarAnalyzer(var myTaintValue: TaintValue) : AbstractUastVisitor() {

      private var stopAnalysis = false
      override fun visitBlockExpression(node: UBlockExpression): Boolean {
        for (expression in node.expressions) {
          expression.accept(this)
          if (stopAnalysis) return true
          if (myTaintValue == TaintValue.TAINTED) {
            return true
          }
        }
        return true
      }

      override fun visitExpression(node: UExpression): Boolean {
        if (skipAfterReference && usedReference == node) {
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
        if (skipClass(uVariable.type)) {
          return TaintValue.UNTAINTED
        }
        if (ClassUtils.isImmutable(uVariable.type)) {
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
                if ((usedReference == null || node.sourcePsi != usedReference.sourcePsi) &&
                    uVariable.sourcePsi != null && node.resolveToUElement()?.sourcePsi == uVariable.sourcePsi) {
                  hasUsage.set(true)
                  return true
                }
              }
              return super.visitExpression(node)
            }

            override fun visitElement(node: UElement): Boolean {
              if (hasUsage.get()) {
                return true
              }
              return super.visitElement(node)
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
        val lhs = (node.leftOperand as? UReferenceExpression) ?: return super.visitBinaryExpression(node)
        val uElement = lhs.resolveToUElement()
        if (uElement == null || uVariable.sourcePsi != uElement.sourcePsi) return super.visitBinaryExpression(node)
        val rhs = node.rightOperand
        myTaintValue = myTaintValue.join(fromExpressionWithoutCollection(rhs, analyzeContext))
        return if (myTaintValue == TaintValue.TAINTED) true else super.visitBinaryExpression(node)
      }
    }

    val varAnalyzer = VarAnalyzer(taintValue)
    codeBlock.accept(varAnalyzer)
    return varAnalyzer.myTaintValue
  }

  private fun fromParam(expression: UExpression, target: PsiElement?, analyzeContext: AnalyzeContext): TaintValue? {
    val psiParameter = (target as? PsiParameter) ?: return null
    val uParameter = target.toUElement(UParameter::class.java) ?: return null
    // default parameter value
    val uInitializer = uParameter.uastInitializer
    var taintValue = fromExpressionWithoutCollection(uInitializer, analyzeContext)
    if (taintValue == TaintValue.TAINTED) return taintValue
    val uMethod = (uParameter.uastParent as? UMethod) ?: return TaintValue.UNTAINTED
    val uBlock = (uMethod.uastBody as? UBlockExpression)
    if (uBlock != null) taintValue = analyzeVar(taintValue, uBlock, uParameter, analyzeContext, expression,
                                                possibleToSkipCheckAfterReference(expression, uMethod))
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

  private fun fromField(expression: UExpression?, target: PsiElement, analyzeContext: AnalyzeContext): TaintValue? {
    if (analyzeContext.depthOutside < 0) return null
    val uElement = target.toUElement() as? UField ?: return null
    val sourcePsi = uElement.sourcePsi ?: return null
    val jvmModifiersOwner: JvmModifiersOwner = uElement
    val equalFiles = equalFiles(analyzeContext, uElement)
    if (!equalFiles &&
        jvmModifiersOwner.hasModifier(JvmModifier.FINAL) &&
        expression is UQualifiedReferenceExpression &&
        skipClass(expression.receiver.getExpressionType())) {
      return TaintValue.UNTAINTED
    }
    if (equalFiles &&
        (jvmModifiersOwner.hasModifier(JvmModifier.FINAL) ||
         (jvmModifiersOwner.hasModifier(JvmModifier.PRIVATE) && fieldAssignedOnlyWithLiterals(target, analyzeContext)))) {
      val result: TaintValue? = fromVar(sourcePsi, analyzeContext.notCollect().minusInside(), expression, false)
      return result ?: TaintValue.UNKNOWN
    }
    if (!equalFiles && jvmModifiersOwner.hasModifier(JvmModifier.FINAL) && uElement.uastInitializer != null) {
      return fromExpressionWithoutCollection(uElement.uastInitializer,
                                             analyzeContext.notNext().minusOutside())
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

  private fun analyzeMethod(uMethod: UMethod, sourceContext: AnalyzeContext, arguments: List<TaintValue>): TaintValue {
    if (!equalFiles(sourceContext, uMethod)) return TaintValue.UNKNOWN
    val psiElement = uMethod.sourcePsi ?: return TaintValue.UNKNOWN
    val key = Pair(psiElement, arguments)
    val value = myVisitedMethods[key]
    if (value != null) {
      return value
    }

    var analyzeContext = sourceContext

    val methodBody = (uMethod.uastBody as? UBlockExpression)
    if (methodBody == null) {
      // maybe it is a generated kotlin property getter or setter
      val sourcePsi = uMethod.sourcePsi ?: return TaintValue.UNTAINTED
      val taintValue = fromField(null, sourcePsi, analyzeContext.minusInside())
      return taintValue ?: TaintValue.UNTAINTED
    }

    analyzeContext = analyzeContext.minusInside().minusMethod()


    class MethodAnalyzer : AbstractUastVisitor() {
      var myTaintValue = TaintValue.UNTAINTED
      override fun visitReturnExpression(node: UReturnExpression): Boolean {
        val returnExpression = node.returnExpression ?: return true
        myTaintValue = myTaintValue.join(fromExpressionWithoutCollection(returnExpression, analyzeContext))
        return if (myTaintValue == TaintValue.TAINTED) true else super.visitReturnExpression(node)
      }
    }

    val methodAnalyzer = MethodAnalyzer()
    myVisitedMethods[key] = TaintValue.UNKNOWN

    val previousVisited = HashMap(myVisited)
    val previousTemporary = HashMap(myVisitedTemporary)

    if (!arguments.isEmpty()) {
      val parameters = uMethod.uastParameters
      for (i in parameters.indices) {
        val sourcePsi = parameters[i].sourcePsi ?: continue
        myVisitedTemporary[sourcePsi] = if (arguments.size <= i) arguments[arguments.size - 1] else arguments[i]
      }
    }

    //prevent recursion always
    myVisited[psiElement] = TaintValue.UNKNOWN

    methodBody.accept(methodAnalyzer)
    val returnValue = methodAnalyzer.myTaintValue

    myVisitedTemporary.clear()
    myVisitedTemporary.putAll(previousTemporary)

    myVisited.clear()
    myVisited.putAll(previousVisited)

    myVisitedMethods[key] = returnValue
    return returnValue
  }

  private fun fromExpressionWithoutCollection(uExpression: UExpression?, analyzeContext: AnalyzeContext): TaintValue {
    return fromExpressionInner(uExpression, analyzeContext.notCollect())
  }

  private fun fromExpressionInner(sourceUExpression: UExpression?, analyzeContext: AnalyzeContext): TaintValue {
    var uExpression = sourceUExpression ?: return TaintValue.UNTAINTED
    if (analyzeContext.depthOutside < 0) return TaintValue.UNKNOWN
    val type = uExpression.getExpressionType()
    if (type != null && skipClass(type)) return TaintValue.UNTAINTED
    uExpression = uExpression.skipParenthesizedExprDown()
    val uConcatenation = getConcatenation(uExpression)
    if (uConcatenation != null) {
      val operands = uConcatenation.operands
      val size = operands.filter { it !is ULiteralExpression && it !is UPolyadicExpression }.size
      return StreamEx.of(operands).collect(joining(analyzeContext.minusInside(size)))
    }
    when (uExpression) {
      is UUnknownExpression -> {
        return TaintValue.UNKNOWN
      }
      is UThisExpression -> {
        return TaintValue.UNTAINTED
      }
      is ULiteralExpression -> {
        return TaintValue.UNTAINTED
      }
      is ULambdaExpression -> {
        return TaintValue.UNKNOWN
      }
      is UClassLiteralExpression -> {
        return TaintValue.UNTAINTED
      }
      is UResolvable -> {
        val resolved = uExpression.resolveToUElement()
        val visited = checkAndPrepareVisited(resolved, prepare = false)
        if (visited != null) {
          return visited
        }
        return analyzeInner(uExpression, analyzeContext.minusInside())
      }
      is UUnaryExpression -> {
        return fromExpressionWithoutCollection(uExpression.operand, analyzeContext.minusInside())
      }
      is UBinaryExpression -> {
        return StreamEx.of(uExpression.leftOperand, uExpression.rightOperand)
          .collect(joining(analyzeContext.minusInside(2)))
      }
      is ULabeledExpression -> {
        return fromExpressionWithoutCollection(uExpression.expression, analyzeContext.minusInside())
      }
      is UIfExpression, is USwitchExpression, is UBlockExpression -> {
        val nonStructuralChildren = nonStructuralChildren(uExpression).toList()
        return StreamEx.of(nonStructuralChildren)
          .filter { it != null }
          .collect(joining(analyzeContext.minusInside(nonStructuralChildren.size)))
      }
      else -> {
        return TaintValue.UNKNOWN
      }
    }
  }

  private fun skipClass(type: PsiType?): Boolean {
    if (type == null) return false
    val aClass = PsiUtil.resolveClassInClassTypeOnly(type)
    return skipClasses.contains(type.canonicalText) ||
           skipClasses.any { cl: String? ->
             cl != null && InheritanceUtil.isInheritor(type, cl)
           } ||
           (aClass != null && (aClass.isAnnotationType || aClass.isEnum))
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

    private fun fieldAssignedOnlyWithLiterals(target: PsiElement, analyzeContext: AnalyzeContext): Boolean {
      val uElement = target.toUElement()
      if (uElement == null) return false
      val containingUClass = uElement.getContainingUClass() ?: return false

      class FieldAssignmentAnalyzer : AbstractUastVisitor() {
        var assignOnlyWithLiteral = true
        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
          if (node.operator !is AssignOperator) return super.visitBinaryExpression(node)
          val lhs = (node.leftOperand as? UReferenceExpression)
          if (lhs == null || target != lhs.resolve()) return super.visitBinaryExpression(node)
          if (node.rightOperand !is ULiteralExpression) {
            //don't go further, consider that it is already untidy
            assignOnlyWithLiteral = false
            return true
          }
          return super.visitBinaryExpression(node)
        }
      }

      val methods = listOf(containingUClass, *containingUClass.innerClasses)
        .map { it.methods.toList() }
        .flatten()
      analyzeContext.minusInside(methods.size)
      return methods.all {
        val visitor = FieldAssignmentAnalyzer()
        if (it.javaPsi.isPhysical) {
          it.accept(visitor)
          return@all visitor.assignOnlyWithLiteral
        }
        val body = it.javaPsi.body.toUElement()
        if (body != null) {
          body.accept(visitor)
          return@all visitor.assignOnlyWithLiteral
        }
        return@all !(it.sourcePsi == target && it.javaPsi.parameters.isNotEmpty())
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

class DeepTaintAnalyzerException : RuntimeException()