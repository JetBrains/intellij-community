// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink

import com.intellij.codeInspection.dataFlow.HardcodedContracts
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.*
import com.intellij.util.SmartList
import com.intellij.util.containers.MultiMap
import com.siyeh.ig.psiutils.ClassUtils
import org.jetbrains.uast.*
import org.jetbrains.uast.UastBinaryOperator.AssignOperator
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.concurrent.atomic.AtomicInteger

class TaintAnalyzer(private val myTaintValueFactory: TaintValueFactory) {
  private val myVisited: MutableMap<PsiElement, TaintValue> = HashMap()
  private val myCurrentParameters: MutableMap<PsiElement, TaintValue> = HashMap()
  private val myVisitedMethods: MutableMap<Pair<PsiElement, List<TaintValue>>, TaintValue> = HashMap()
  private val myNonMarkedElements: MutableList<NonMarkedElement> = ArrayList()

  private val skipClasses: Set<String> = myTaintValueFactory.getConfiguration()
    .skipClasses
    .filterNotNull()
    .toSet()

  @JvmOverloads
  @Throws(DeepTaintAnalyzerException::class)
  fun analyzeExpression(expression: UExpression,
                        collectOuterUsages: Boolean,
                        untilTaintValue: TaintValue = TaintValue.TAINTED): TaintValue {
    val file = expression.getContainingUFile() ?: return TaintValue.UNKNOWN
    val context = AnalyzeContext.create(
      processOuterMethodAsQualifierAndArguments = myTaintValueFactory.getConfiguration().processOuterMethodAsQualifierAndArguments,
      processInnerMethodAsQualifierAndArguments = myTaintValueFactory.getConfiguration().processInnerMethodAsQualifierAndArguments,
      file = file,
      collectReferences = collectOuterUsages,
      processOnlyConstant = false,
      depthOutside = 1,
      parts = 20,
      depthInside = 5,
      depthNestedMethods = 1,
      next = true,
      untilTaintValue = untilTaintValue,
      parameterOfPrivateMethodIsUntainted = myTaintValueFactory.getConfiguration().parameterOfPrivateMethodIsUntainted)
    return fromExpressionInner(expression, context)
  }

  private data class AnalyzeContext(
    //use to check only qualifiers and arguments for outer methods
    val processOuterMethodAsQualifierAndArguments: Boolean,
    //use to check only qualifiers and arguments for inner methods
    val processInnerMethodAsQualifierAndArguments: Boolean,
    //file for target expression
    val file: UFile,
    //used for propagation tree to collect assignments for fields and variables
    val collectReferences: Boolean,
    //used for propagation tree to check if it is necessary to collect non-marked elements by default
    val collectMarkedByDefault: Boolean,
    //used to limit processing outer files with fields
    val processOnlyConstant: Boolean,
    //used to limit processing outer files for methods and fields
    val depthOutside: Int,
    //current processed parts
    private val parts: AtomicInteger,
    //current resolving depth
    private val inside: Int,
    private val depthNestedMethods: Int,
    //mark for propagation tree that it is a leaf
    val checkPropagationNext: Boolean,
    val untilTaintValue: TaintValue,
    val parameterOfPrivateMethodIsUntainted: Boolean) {
    companion object {
      fun create(processOuterMethodAsQualifierAndArguments: Boolean,
                 processInnerMethodAsQualifierAndArguments: Boolean,
                 file: UFile,
                 collectReferences: Boolean,
                 processOnlyConstant: Boolean,
                 depthOutside: Int,
                 parts: Int,
                 depthInside: Int,
                 depthNestedMethods: Int,
                 next: Boolean,
                 untilTaintValue: TaintValue,
                 parameterOfPrivateMethodIsUntainted: Boolean): AnalyzeContext {
        return AnalyzeContext(processOuterMethodAsQualifierAndArguments = processOuterMethodAsQualifierAndArguments,
                              processInnerMethodAsQualifierAndArguments = processInnerMethodAsQualifierAndArguments,
                              file = file,
                              collectReferences = collectReferences,
                              processOnlyConstant = processOnlyConstant,
                              depthOutside = depthOutside,
                              parts = AtomicInteger(parts),
                              inside = depthInside,
                              depthNestedMethods = depthNestedMethods,
                              checkPropagationNext = next,
                              untilTaintValue = untilTaintValue,
                              collectMarkedByDefault = true,
                              parameterOfPrivateMethodIsUntainted = parameterOfPrivateMethodIsUntainted)
      }
    }

    fun minusMethod(): AnalyzeContext {
      val depth = depthNestedMethods - 1
      if (depth < 0) {
        throw DeepTaintAnalyzerException()
      }
      return copy(depthNestedMethods = depth)
    }

    fun minusPart(): AnalyzeContext {
      val depth = parts.decrementAndGet()
      if (depth < 0) {
        throw DeepTaintAnalyzerException()
      }
      return this
    }

    fun minusInside(): AnalyzeContext {
      if (inside < 0) {
        throw DeepTaintAnalyzerException()
      }
      return copy(inside = inside - 1)
    }

    fun minusPart(size: Int): AnalyzeContext {
      val previous = parts.get()
      val next = previous - size
      parts.set(next)
      if (next < 0) {
        throw DeepTaintAnalyzerException()
      }
      return this
    }

    fun notCollectReferences(): AnalyzeContext {
      if (collectReferences) {
        return this.copy(collectReferences = false)
      }
      return this
    }

    fun notCollectMarkedByDefault(): AnalyzeContext {
      if (collectMarkedByDefault) {
        return this.copy(collectMarkedByDefault = false)
      }
      return this
    }

    fun minusOutside(): AnalyzeContext {
      return this.copy(processOnlyConstant = true, depthOutside = depthOutside - 1)
    }

    fun notCheckPropagationNext(): AnalyzeContext {
      if (checkPropagationNext) {
        return this.copy(checkPropagationNext = false)
      }
      return this
    }

    fun checkInside(size: Int) {
      if (parts.get() - size < 0) {
        throw DeepTaintAnalyzerException()
      }
    }
  }

  private fun analyzeInner(expression: UExpression, analyzeContext: AnalyzeContext): TaintValue {
    val uResolvable = (expression as? UResolvable) ?: return TaintValue.UNKNOWN
    if (expression.sourcePsi == null) return TaintValue.UNKNOWN
    val sourceTarget = uResolvable.resolve()
    if (sourceTarget == null) {
      return fromCall(expression, analyzeContext) ?: TaintValue.UNKNOWN
    }
    var taintValue: TaintValue = myTaintValueFactory.fromElement(sourceTarget) ?: TaintValue.UNKNOWN
    if (taintValue != TaintValue.UNKNOWN) return taintValue
    val value = checkAndPrepareVisited(expression)
    if (value != null) return value
    taintValue = fromModifierListOwner(sourceTarget, expression, analyzeContext) ?: TaintValue.UNKNOWN
    addToVisited(expression, taintValue)
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
    myVisited[sourcePsi] = result
  }

  private fun fromCall(sourceExpression: UExpression, sourceContext: AnalyzeContext): TaintValue? {
    var expression = sourceExpression
    var analyzeContext = sourceContext
    if (analyzeContext.processOnlyConstant) {
      return null
    }

    if (!analyzeContext.collectReferences) {
      analyzeContext = analyzeContext.notCollectMarkedByDefault()
    }

    //fields as methods
    if (expression is UQualifiedReferenceExpression && expression.selector is UReferenceExpression) {
      val uMethod = expression.resolveToUElement()
      if (uMethod is UMethod) {
        val equalFiles = equalFiles(analyzeContext, uMethod)
        if (myTaintValueFactory.getConfiguration().processOuterMethodAsQualifierAndArguments && !equalFiles) {
          val fromReceiver = fromExpressionWithoutCollection(expression.receiver, analyzeContext.notCollectMarkedByDefault())
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
      expression.indices.forEach {
        result = result.joinUntil(analyzeContext.untilTaintValue) {
          fromExpressionWithoutCollection(it, analyzeContext)
        }
      }
      return result
    }
    if (expression !is UCallExpression) {
      return null
    }

    val uMethod = expression.resolveToUElement()
    if (uMethod is UMethod && equalFiles(analyzeContext, uMethod) &&
        !uMethod.isConstructor && !analyzeContext.processInnerMethodAsQualifierAndArguments) {
      val jvmModifiersOwner: JvmModifiersOwner = uMethod
      val fromReceiver = fromExpressionWithoutCollection(expression.receiver, analyzeContext)
      if (fromReceiver == TaintValue.UNTAINTED &&
          (jvmModifiersOwner.hasModifier(JvmModifier.FINAL) || jvmModifiersOwner.hasModifier(JvmModifier.STATIC) ||
           jvmModifiersOwner.hasModifier(JvmModifier.PRIVATE))) {
        return analyzeMethod(uMethod, analyzeContext, getNotEmptyParameters(uMethod, expression, analyzeContext))
      }
      else {
        //only to show tree
        if (analyzeContext.collectReferences) {
          analyzeMethod(uMethod, analyzeContext.notCollectReferences().notCheckPropagationNext(), getEmptyParameters(uMethod))
        }
        return TaintValue.UNKNOWN
      }
    }
    if (analyzeContext.processOuterMethodAsQualifierAndArguments || analyzeContext.processInnerMethodAsQualifierAndArguments) {
      var taintValue = fromExpressionWithoutCollection(expression.receiver, analyzeContext)
      analyzeContext.checkInside(expression.valueArguments.size)
      expression.valueArguments.forEach { argument ->
        taintValue = taintValue.joinUntil(analyzeContext.untilTaintValue) { fromExpressionWithoutCollection(argument, analyzeContext) }
      }
      return taintValue
    }
    return TaintValue.UNKNOWN
  }

  private fun getNotEmptyParameters(uMethod: UMethod, expression: UCallExpression, analyzeContext: AnalyzeContext): List<TaintValue> {
    val parameterSize = uMethod.uastParameters.size
    analyzeContext.checkInside(expression.valueArguments.size)
    val values: MutableList<TaintValue> = mutableListOf()
    for (i in 0 until parameterSize) {
      val argument = expression.getArgumentForParameter(i)
      if (argument is UExpressionList) {
        values.add(TaintValue.UNKNOWN)
      }
      else {
        values.add(fromExpressionWithoutCollection(argument, analyzeContext))
      }
    }
    return values
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
    addNonMarked(analyzeContext, taintValue, expression, owner)
    return taintValue ?: TaintValue.UNKNOWN
  }

  private fun addNonMarked(analyzeContext: AnalyzeContext,
                           taintValue: TaintValue?,
                           expression: UExpression,
                           owner: PsiModifierListOwner) {
    if (!analyzeContext.collectReferences && analyzeContext.collectMarkedByDefault && (taintValue == null || taintValue == TaintValue.UNKNOWN)) {
      val sourcePsi = expression.sourcePsi
      if (sourcePsi != null) {
        myNonMarkedElements.add(NonMarkedElement(owner, sourcePsi, analyzeContext.checkPropagationNext))
      }
    }
  }

  private fun fromLocalVar(expression: UExpression, sourceTarget: PsiElement, analyzeContext: AnalyzeContext): TaintValue? {
    if (sourceTarget !is PsiLocalVariable) return null
    val localVariable = (expression as? UResolvable)?.resolveToUElement() as? ULocalVariable ?: return null
    val containingMethod = localVariable.getContainingUMethod() ?: return null
    val skipAfterReference = possibleToSkipCheckAfterReference(expression, containingMethod)
    val uInitializer = localVariable.uastInitializer
    val taintValue = fromExpressionWithoutCollection(uInitializer, analyzeContext)
    val uMethod: UMethod? = localVariable.getParentOfType(UMethod::class.java)
    return uMethod?.let {
      taintValue.joinUntil(analyzeContext.untilTaintValue) {
        analyzeVar(taintValue, it, localVariable, analyzeContext, expression, skipAfterReference)
      }
    } ?: taintValue
  }

  //Kotlin allows use non-effective-final variables in lambdas, in java containers can be spoilt
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

  private fun analyzeVar(initValue: TaintValue,
                         uMethod: UMethod,
                         uVariable: UVariable,
                         analyzeContext: AnalyzeContext,
                         usedReference: UExpression?,
                         skipAfterReference: Boolean): TaintValue {
    val sourcePsi = uMethod.sourcePsi
    if (sourcePsi == null) return TaintValue.UNKNOWN
    val variableFlow = getVariableFlow(sourcePsi)

    return variableFlow.process(this, initValue, uVariable, analyzeContext, usedReference, skipAfterReference)
  }


  private fun fromParam(expression: UExpression, target: PsiElement?, analyzeContext: AnalyzeContext): TaintValue? {
    val psiParameter = (target as? PsiParameter) ?: return null
    val uParameter = target.toUElement(UParameter::class.java) ?: return null
    val sourcePsi = uParameter.sourcePsi
    //kotlin parameters
    if (expression is UQualifiedReferenceExpression && analyzeContext.processOuterMethodAsQualifierAndArguments) {
      return fromExpressionWithoutCollection(expression.receiver, analyzeContext)
    }
    var taintValue = TaintValue.UNTAINTED // by default
    val uMethod = (uParameter.uastParent as? UMethod) ?: return TaintValue.UNKNOWN
    val psiMethod = (uMethod.sourcePsi as? PsiMethod)

    //from method invocation
    var fromInvocation = false
    if (sourcePsi != null) {
      val previousValue = myCurrentParameters[sourcePsi]
      if (previousValue != null) {
        fromInvocation = true
        taintValue = previousValue
      }
    }

    if (psiMethod != null && psiMethod.hasModifier(JvmModifier.PRIVATE) && !fromInvocation) {
      if (analyzeContext.parameterOfPrivateMethodIsUntainted) {
        taintValue = taintValue.join(TaintValue.UNTAINTED)
      }
      else {
        taintValue = taintValue.join(TaintValue.UNKNOWN)
      }
    }
    else if (!fromInvocation) {
      taintValue = taintValue.join(TaintValue.UNKNOWN)
    }

    // default parameter value
    val uInitializer = uParameter.uastInitializer
    taintValue = taintValue.joinUntil(analyzeContext.untilTaintValue) { fromExpressionWithoutCollection(uInitializer, analyzeContext) }
    val uBlock = (uMethod.uastBody as? UBlockExpression)
    if (uBlock != null) {
      taintValue = taintValue.joinUntil(analyzeContext.untilTaintValue) {
        analyzeVar(taintValue, uMethod, uParameter, analyzeContext, expression, possibleToSkipCheckAfterReference(expression, uMethod))
      }
    }
    val nonMarkedElements = SmartList<NonMarkedElement?>()
    // this might happen when we analyze kotlin primary constructor parameter
    if (uBlock == null && analyzeContext.collectReferences && taintValue != TaintValue.TAINTED) {
      nonMarkedElements.addAll(findAssignments(psiParameter))
    }

    if (psiMethod != null && analyzeContext.collectReferences && taintValue != TaintValue.TAINTED) {
      val paramIdx = uMethod.uastParameters.indexOf(uParameter)
      nonMarkedElements.addAll(findNonMarkedArgs(psiMethod, paramIdx))
    }
    myNonMarkedElements.addAll(nonMarkedElements.filterNotNull())
    return taintValue
  }

  private fun fromField(expression: UExpression?, target: PsiElement, analyzeContext: AnalyzeContext): TaintValue? {
    //kotlin constructor parameters are considered as parameters
    if (analyzeContext.depthOutside < 0) return null
    val uElement = target.toUElement() as? UField ?: return null
    val jvmModifiersOwner: JvmModifiersOwner = uElement
    val equalFiles = equalFiles(analyzeContext, uElement)
    if (!equalFiles &&
        jvmModifiersOwner.hasModifier(JvmModifier.FINAL) &&
        expression is UQualifiedReferenceExpression &&
        skipClass(expression.receiver.getExpressionType())) {
      return TaintValue.UNTAINTED
    }
    if (equalFiles &&
        (jvmModifiersOwner.hasModifier(JvmModifier.FINAL) &&
         (uElement.uastInitializer != null || fieldAssignedOnlyWithLiterals(uElement, analyzeContext)) ||
         (jvmModifiersOwner.hasModifier(JvmModifier.PRIVATE) && fieldAssignedOnlyWithLiterals(uElement, analyzeContext)))) {
      val uastInitializer = uElement.uastInitializer
      if (uastInitializer == null) return TaintValue.UNTAINTED
      return fromExpressionWithoutCollection(uElement.uastInitializer, analyzeContext.notCheckPropagationNext())
    }
    if (!equalFiles && jvmModifiersOwner.hasModifier(JvmModifier.FINAL) && uElement.uastInitializer != null) {
      return fromExpressionWithoutCollection(uElement.uastInitializer, analyzeContext.notCheckPropagationNext().minusOutside())
    }
    if (analyzeContext.processOnlyConstant) {
      return null
    }
    if (analyzeContext.collectReferences) {
      val children: MutableList<NonMarkedElement?> = ArrayList()
      val initializer = NonMarkedElement.create(uElement.uastInitializer, analyzeContext.checkPropagationNext)
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
    return analyzeMethod(uMethod, analyzeContext, getEmptyParameters(uMethod))
  }

  private fun getEmptyParameters(uMethod: UMethod): List<TaintValue> {
    val values: MutableList<TaintValue> = ArrayList()
    val parameters = uMethod.uastParameters
    for (i in parameters.indices) {
      values.add(TaintValue.UNKNOWN)
    }
    return values
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
    val sourcePsi = uMethod.sourcePsi ?: return TaintValue.UNKNOWN
    if (methodBody == null) {
      // maybe it is a generated kotlin property getter or setter
      val taintValue = fromField(null, sourcePsi, analyzeContext.minusInside())
      return taintValue ?: TaintValue.UNKNOWN
    }

    analyzeContext = analyzeContext.minusMethod()

    val allReturns = getAllReturns(sourcePsi)

    myVisitedMethods[key] = TaintValue.UNKNOWN

    val previousVisited = HashMap(myVisited)
    val previousTemporary = HashMap(myCurrentParameters)

    if (!arguments.isEmpty()) {
      val parameters = uMethod.uastParameters
      for (i in parameters.indices) {
        val parameterSourcePsi = parameters[i].sourcePsi ?: continue
        if (arguments.size <= i) continue
        myCurrentParameters[parameterSourcePsi] = arguments[i]
      }
    }

    //prevent recursion always
    myVisited[psiElement] = TaintValue.UNKNOWN

    val returnValue = allReturns.join(analyzeContext.minusPart(allReturns.size))

    myCurrentParameters.clear()
    myCurrentParameters.putAll(previousTemporary)

    myVisited.clear()
    myVisited.putAll(previousVisited)

    myVisitedMethods[key] = returnValue
    return returnValue
  }

  private fun fromExpressionWithoutCollection(uExpression: UExpression?, analyzeContext: AnalyzeContext): TaintValue {
    return fromExpressionInner(uExpression, analyzeContext.notCollectReferences())
  }

  private fun fromExpressionInner(sourceUExpression: UExpression?, analyzeContext: AnalyzeContext): TaintValue {
    var uExpression = sourceUExpression ?: return TaintValue.UNTAINTED //may be null as receiver
    if (analyzeContext.depthOutside < 0) return TaintValue.UNKNOWN
    val type = uExpression.getExpressionType()
    if (type != null && skipClass(type)) return TaintValue.UNTAINTED
    uExpression = uExpression.skipParenthesizedExprDown()
    val uConcatenation = getConcatenation(uExpression)
    if (uConcatenation != null) {
      val operands = uConcatenation.operands
      val size = operands.filter { it !is ULiteralExpression && it !is UPolyadicExpression }.size
      return withCache(uExpression) { operands.join(analyzeContext.minusPart(size)) }
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
        return withCache(uExpression) { analyzeInner(uExpression, analyzeContext.minusInside()) }
      }
      is UUnaryExpression -> {
        return withCache(uExpression) { fromExpressionWithoutCollection(uExpression.operand, analyzeContext.minusPart()) }
      }
      is UBinaryExpression -> {
        return withCache(uExpression) {
          setOf(uExpression.leftOperand, uExpression.rightOperand).join(analyzeContext.minusPart(2))
        }
      }
      is ULabeledExpression -> {
        return withCache(uExpression) { fromExpressionWithoutCollection(uExpression.expression, analyzeContext.minusPart()) }
      }
      is UIfExpression, is USwitchExpression, is UBlockExpression -> {
        return withCache(uExpression) {
          val nonStructuralChildren = nonStructuralChildren(uExpression).toList()
          nonStructuralChildren
            .filterNotNull()
            .join(analyzeContext.minusPart(nonStructuralChildren.size))
        }
      }
      else -> {
        return TaintValue.UNKNOWN
      }
    }
  }

  private fun withCache(uExpression: UExpression, block: () -> TaintValue): TaintValue {
    val visited = checkAndPrepareVisited(uExpression, prepare = false)
    if (visited != null) return visited
    val taintValue = block.invoke()
    addToVisited(uExpression, taintValue)
    return taintValue
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

  private fun Iterable<UExpression>.join(analyzeContext: AnalyzeContext): TaintValue {
    var result = TaintValue.UNTAINTED
    this.forEach {
      result = result.joinUntil(analyzeContext.untilTaintValue) { fromExpressionWithoutCollection(it, analyzeContext) }
    }
    return result
  }

  companion object {

    private fun getAllReturns(sourcePsi: PsiElement): MutableSet<UExpression> =
      CachedValuesManager.getManager(sourcePsi.project)
        .getCachedValue(sourcePsi, CachedValueProvider {
          val method = sourcePsi.toUElement() as? UMethod
          val visitor = object : AbstractUastVisitor() {
            val returns = mutableSetOf<UExpression>()
            override fun visitReturnExpression(node: UReturnExpression): Boolean {
              val returnExpression = node.returnExpression ?: return super.visitReturnExpression(node)
              returns.add(returnExpression)
              return super.visitReturnExpression(node)
            }
          }
          method?.uastBody?.accept(visitor)
          return@CachedValueProvider CachedValueProvider.Result.create(visitor.returns, PsiModificationTracker.MODIFICATION_COUNT)
        })

    private fun getVariableFlow(sourcePsi: PsiElement): VariableFlow =
      CachedValuesManager.getManager(sourcePsi.project)
        .getCachedValue(sourcePsi, CachedValueProvider {
          val method = sourcePsi.toUElement() as? UMethod
          val visitor = object : AbstractUastVisitor() {
            val flow = VariableFlow()

            override fun afterVisitExpression(node: UExpression) {
              val currentVariable = (node as? UResolvable)?.resolveToUElement() as? UVariable ?: return
              if (!(currentVariable is ULocalVariable || currentVariable is UParameter)) return
              flow.addUsage(currentVariable, node)
            }

            override fun afterVisitCallExpression(node: UCallExpression) {
              val javaPsi = (node.resolveToUElement() as? UMethod)?.javaPsi
              if (javaPsi != null && JavaMethodContractUtil.isPure(javaPsi)) {
                return
              }
              val receiver = node.receiver
              if (receiver != null) {
                checkUsages(listOf(receiver), node.valueArguments)
              }
              if (javaPsi != null && HardcodedContracts.isKnownNoParameterLeak(javaPsi)) {
                return
              }
              checkUsages(node.valueArguments, null)
              return
            }

            override fun afterVisitBinaryExpression(node: UBinaryExpression) {
              if (node.operator !is AssignOperator) return
              val lhs = (node.leftOperand as? UReferenceExpression) ?: return
              val uElement = lhs.resolveToUElement() as? UVariable ?: return
              if (!(uElement is ULocalVariable || uElement is UParameter)) return
              val rhs = node.rightOperand
              flow.addAssign(uElement, rhs)
            }

            private fun checkUsages(expressions: List<UExpression?>, dependsOn: List<UExpression?>?) {
              for (expression in expressions) {
                if (expression == null) continue
                val currentVariable = (expression as? UResolvable)?.resolveToUElement() as? UVariable ?: continue
                if (!(currentVariable is ULocalVariable || currentVariable is UParameter)) continue
                flow.addDropLocality(currentVariable, dependsOn)
              }
            }
          }
          method?.uastBody?.accept(visitor)
          return@CachedValueProvider CachedValueProvider.Result.create(visitor.flow, PsiModificationTracker.MODIFICATION_COUNT)
        })

    private fun equalFiles(analyzeContext: AnalyzeContext, method: UElement): Boolean {
      val file = method.getContainingUFile()
      return file != null && analyzeContext.file.sourcePsi == file.sourcePsi
    }

    private fun fieldAssignedOnlyWithLiterals(field: UField, analyzeContext: AnalyzeContext): Boolean {
      val containingUClass = field.getContainingUClass() ?: return false
      val parentSourcePsiForCache = containingUClass.sourcePsi ?: return false
      val target = field.sourcePsi

      val cachedAssignedFields = CachedValuesManager.getManager(parentSourcePsiForCache.project)
        .getCachedValue(parentSourcePsiForCache, CachedValueProvider {
          val visitor = object : AbstractUastVisitor() {
            val assignedFields: MutableSet<UField> = hashSetOf()
            override fun visitMethod(node: UMethod): Boolean {
              //records
              if (!node.javaPsi.isPhysical && node.uastBody == null) {
                node.javaPsi.body.toUElement()?.accept(this)
              }
              return super.visitMethod(node)
            }

            override fun visitBinaryExpression(
              node: UBinaryExpression): Boolean {
              if (node.operator !is AssignOperator) return super.visitBinaryExpression(node)
              val sourcePsi = node.sourcePsi
              if (sourcePsi == null) return super.visitBinaryExpression(node)
              val lhs = (node.leftOperand as? UReferenceExpression) ?: return super.visitBinaryExpression(node)
              val uField = (lhs.resolveToUElement() as? UField) ?: return super.visitBinaryExpression(node)
              if (node.rightOperand !is ULiteralExpression) {
                assignedFields.add(uField)
              }
              return super.visitBinaryExpression(node)
            }
          }
          val uClass = parentSourcePsiForCache.toUElement()
          uClass?.accept(visitor)
          return@CachedValueProvider CachedValueProvider.Result.create(visitor.assignedFields, PsiModificationTracker.MODIFICATION_COUNT)
        })
      if (cachedAssignedFields.contains(field)) return false

      val methods = CachedValuesManager.getManager(parentSourcePsiForCache.project)
        .getCachedValue(parentSourcePsiForCache, CachedValueProvider {
          val uClass = parentSourcePsiForCache.toUElement() as? UClass
          val uMethods = if (uClass != null) {
            listOf(uClass, *uClass.innerClasses)
              .map { it.methods.toList() }
              .flatten()
          }
          else {
            listOf()
          }
          return@CachedValueProvider CachedValueProvider.Result.create(uMethods, PsiModificationTracker.MODIFICATION_COUNT)
        })

      analyzeContext.minusPart(methods.size)
      return methods.none {
        //kotlin setters, lombok
        (it.sourcePsi == target && it.javaPsi.parameters.isNotEmpty())
      }
    }

    private fun findNonMarkedArgs(psiMethod: PsiMethod, paramIdx: Int): Collection<NonMarkedElement?> {
      return findArgs(psiMethod, paramIdx).mapNotNull { arg -> NonMarkedElement.create(arg, false) }
    }

    private fun findArgs(psiMethod: PsiMethod, paramIdx: Int): Collection<UExpression?> {
      return CachedValuesManager.getManager(psiMethod.project)
        .getCachedValue(psiMethod, CachedValueProvider {
          return@CachedValueProvider CachedValueProvider.Result.create(
            ReferencesSearch.search(psiMethod, psiMethod.useScope)
              .mapping { it.element.parent as? PsiMethodCallExpression }
              .mapping { it?.argumentList?.expressions }, PsiModificationTracker.MODIFICATION_COUNT)
        }).filtering { args -> args != null && args.size > paramIdx }
        .mapping { args -> args[paramIdx].toUElement() as? UExpression }
        .findAll()
    }

    private fun findAssignments(target: PsiElement): Collection<NonMarkedElement?> {
      return ReferencesSearch.search(target, target.useScope)
        .mapping { u: PsiReference -> u.element.getUastParentOfType(UBinaryExpression::class.java) }
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

  private class VariableFlow {
    val variables: MultiMap<PsiElement, VariableStep> = MultiMap()

    fun addDropLocality(currentVariable: UVariable, dependsOn: List<UExpression?>?) {
      val psiElement = currentVariable.sourcePsi ?: return
      variables.putValue(psiElement, DropLocalityStep(dependsOn))
    }

    fun addUsage(currentVariable: UVariable, usage: UExpression) {
      val psiElement = currentVariable.sourcePsi ?: return
      val psiElementUsage = usage.sourcePsi ?: return
      val variableSteps = variables[psiElement]
      val lastStep = variableSteps.lastOrNull()
      if (lastStep is UsagesStep) {
        lastStep.usages.add(psiElementUsage)
      }
      else {
        variables.putValue(psiElement, UsagesStep(usages = mutableSetOf(psiElementUsage)))
      }
    }

    fun addAssign(currentVariable: UVariable, assign: UExpression) {
      val psiElement = currentVariable.sourcePsi ?: return
      variables.putValue(psiElement, DependsOnStep(assign))
    }

    fun process(taintAnalyzer: TaintAnalyzer,
                initValue: TaintValue,
                uVariable: UVariable,
                analyzeContext: AnalyzeContext,
                usedReference: UExpression?,
                skipAfterReference: Boolean): TaintValue {
      val psiElement = uVariable.sourcePsi ?: return TaintValue.UNKNOWN
      val variableSteps = variables[psiElement]
      if (variableSteps.isEmpty()) return initValue
      var resultValue = initValue

      for (variableStep in variableSteps) {
        when (variableStep) {
          is DropLocalityStep -> {
            var taintValue: TaintValue? = null
            if (taintAnalyzer.skipClass(uVariable.type) || ClassUtils.isImmutable(uVariable.type)) {
              taintValue = TaintValue.UNTAINTED
            }
            else if (variableStep.dependsOn != null && analyzeContext.processOuterMethodAsQualifierAndArguments) {
              var processValue = TaintValue.UNTAINTED
              analyzeContext.checkInside(variableStep.dependsOn.size)
              for (relativeExpression in variableStep.dependsOn) {
                if (relativeExpression != null) {
                  processValue = processValue.joinUntil(analyzeContext.untilTaintValue) {
                    taintAnalyzer.fromExpressionWithoutCollection(relativeExpression, analyzeContext)
                  }
                }
                taintValue = processValue
              }
            }
            else {
              taintValue = TaintValue.UNKNOWN
            }
            if (taintValue != null) {
              resultValue = resultValue.join(taintValue)
            }
          }
          is UsagesStep -> {
            if (skipAfterReference && usedReference != null) {
              val sourcePsi = usedReference.sourcePsi
              if (sourcePsi != null && variableStep.usages.contains(sourcePsi)) {
                return resultValue //fast exit
              }
            }
          }
          is DependsOnStep -> {
            val expression = variableStep.expression
            resultValue = resultValue.joinUntil(analyzeContext.untilTaintValue) {
              taintAnalyzer.fromExpressionWithoutCollection(expression, analyzeContext)
            }
          }
        }
      }

      return resultValue
    }
  }
}

private interface VariableStep

private class DropLocalityStep(val dependsOn: List<UExpression?>?) : VariableStep

private data class UsagesStep(val usages: MutableSet<PsiElement> = hashSetOf()) : VariableStep

private class DependsOnStep(val expression: UExpression) : VariableStep

class DeepTaintAnalyzerException : RuntimeException()