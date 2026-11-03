// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.actions.JavaSmartStepIntoBytecodeMatcher.PreparedBytecode
import com.intellij.debugger.engine.ContextUtil
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.JvmMethodMatcher
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator
import com.intellij.debugger.engine.withDebugContext
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.impl.runBlockingAssertNotInReadAction
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.statistics.DebuggerStatistics.logSmartStepIntoTargetsDetection
import com.intellij.debugger.statistics.Engine
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.filters.LineNumbersMapping
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiConditionalExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiEnumConstantInitializer
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiField
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiStatement
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.DocumentUtil
import com.intellij.util.Range
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asCancellablePromise
import org.jetbrains.concurrency.rejectedPromise

internal open class JavaSmartStepIntoHandler : JvmSmartStepIntoHandler() {
  override fun isAvailable(position: SourcePosition): Boolean = position.file.language.isKindOf(JavaLanguage.INSTANCE)

  override fun findSmartStepTargetsAsync(position: SourcePosition, session: DebuggerSession): Promise<List<SmartStepTarget>> =
    findSmartStepTargetsAsync(position, session, true)

  override fun findStepIntoTargets(position: SourcePosition, session: DebuggerSession): Promise<List<SmartStepTarget>> {
    if (!DebuggerSettings.getInstance().ALWAYS_SMART_STEP_INTO) return rejectedPromise()
    return findSmartStepTargetsAsync(position, session, false)
  }

  private fun findSmartStepTargetsAsync(
    position: SourcePosition,
    session: DebuggerSession,
    smart: Boolean,
  ): Promise<List<SmartStepTarget>> {
    val context = session.contextManager.context
    val suspendContext = context.suspendContext ?: return rejectedPromise()
    return suspendContext.coroutineScope.async {
      withDebugContext(context, PrioritizedTask.Priority.NORMAL) {
        if (ContextUtil.getSourcePosition(suspendContext) != position) throw CancellationException()
        findStepTargetsAsync(position, suspendContext, context, smart) {
          if (session.contextManager.context !== context) throw CancellationException()
        }
      }
    }.asCompletableFuture().asCancellablePromise()
  }

  override fun findSmartStepTargets(position: SourcePosition?): List<SmartStepTarget> {
    throw IllegalStateException("Should not be used")
  }

  @TestOnly
  @JvmName("findStepTargets")
  @RequiresBlockingContext
  @RequiresReadLockAbsence
  internal fun findStepTargets(
    position: SourcePosition,
    suspendContext: SuspendContextImpl?,
    debuggerContext: DebuggerContextImpl,
    smart: Boolean,
  ): List<SmartStepTarget> = runBlockingAssertNotInReadAction {
    findStepTargetsAsync(position, suspendContext, debuggerContext, smart)
  }

  @RequiresReadLockAbsence
  private suspend fun findStepTargetsAsync(
    position: SourcePosition,
    suspendContext: SuspendContextImpl?,
    debuggerContext: DebuggerContextImpl,
    smart: Boolean,
    checkContext: () -> Unit = {},
  ): List<SmartStepTarget> {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    ThreadingAssertions.assertNoReadAccess()
    val project = requireNotNull(debuggerContext.project)
    while (true) {
      currentCoroutineContext().ensureActive()
      checkContext()
      val plan = readAction { JavaSmartStepIntoPlan.build(position, project) } ?: return emptyList()
      currentCoroutineContext().ensureActive()
      checkContext()
      try {
        val selection = plan.evaluate(debuggerContext.createEvaluationContext())
        val frame = suspendContext?.frameProxy
        val bytecode = if (frame != null) {
          val vm = frame.virtualMachine
          if (vm.canGetConstantPool() && vm.canGetBytecodes()) {
            PreparedBytecode.prepare(frame.location(), plan.lines, plan.methods, suspendContext.debugProcess)
          }
          else if (!smart) {
            logSmartStepIntoTargetsDetection(project, Engine.JAVA, SmartStepIntoDetectionStatus.BYTECODE_NOT_AVAILABLE)
            return emptyList()
          }
          else null
        }
        else null
        val result = readAction {
          if (!plan.isUpToDate(project)) null
          else filterTargets(plan, selection, bytecode, debuggerContext, smart)
        } ?: continue
        currentCoroutineContext().ensureActive()
        checkContext()
        if (result.error != null) {
          JvmSmartStepIntoErrorReporter.report(result.error, debuggerContext.debuggerSession)
        }
        logSmartStepIntoTargetsDetection(project, Engine.JAVA, result.status)
        return result.targets
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        if (!readAction { plan.isUpToDate(project) }) continue
        DebuggerUtilsImpl.logError(e)
        logSmartStepIntoTargetsDetection(project, Engine.JAVA, SmartStepIntoDetectionStatus.INTERNAL_ERROR)
        return emptyList()
      }
    }
  }

  @RequiresReadLock
  private fun filterTargets(
    plan: JavaSmartStepIntoPlan,
    selection: BooleanArray,
    bytecode: PreparedBytecode?,
    debuggerContext: DebuggerContextImpl,
    smart: Boolean,
  ): SearchResult {
    val [matchingTargets, selectedTargets] = plan.createTargets(selection)
    if (selectedTargets.isEmpty()) return SearchResult(emptyList(), SmartStepIntoDetectionStatus.NO_TARGETS)
    if (bytecode != null) {
      val [notFoundTargets, collidingTargets, alreadyExecutedTargets, conditionallyExecutedTargets] =
        JavaSmartStepIntoBytecodeMatcher(bytecode, requireNotNull(debuggerContext.debugProcess), matchingTargets, !smart).match()
      val collisions = collidingTargets.filter { it in selectedTargets }
      val notFound = notFoundTargets.filter { it in selectedTargets }
      val errorMessage = buildList {
        if (collisions.isNotEmpty()) {
          add("Target occurred multiple times in bytecode: ${JvmSmartStepIntoErrorReporter.joinTargetInfo(collisions)}")
        }
        if (notFound.isNotEmpty()) {
          add("Target not found in bytecode: ${JvmSmartStepIntoErrorReporter.joinTargetInfo(notFound)}")
        }
      }.joinToString("\n")
      if (errorMessage.isNotEmpty()) {
        val error = JvmSmartStepIntoErrorReporter.SourceInfo(
          plan.element.text, plan.element.containingFile.virtualFile.canonicalPath, errorMessage
        )
        return SearchResult(emptyList(), SmartStepIntoDetectionStatus.TARGETS_MISMATCH, error)
      }
      selectedTargets.removeAll(alreadyExecutedTargets)
      val conditionalTargets = conditionallyExecutedTargets.filterTo(HashSet()) { it in selectedTargets }
      if (!smart && selectedTargets.isNotEmpty() && conditionalTargets.isNotEmpty()) {
        val oldSize = selectedTargets.size
        selectedTargets.removeAll(conditionalTargets)
        assert(oldSize == selectedTargets.size + conditionalTargets.size)
        if (selectedTargets.isNotEmpty() && selectedTargets.none { it is MethodSmartStepTarget && !it.needsBreakpointRequest() }) {
          selectedTargets.addAll(conditionalTargets)
        }
      }
    }

    val removed = matchingTargets.filter { it !in selectedTargets }
    for (target in removed) {
      if (target !is MethodSmartStepTarget || target.needsBreakpointRequest()) continue
      val method = target.method
      for (other in matchingTargets) {
        if (other is MethodSmartStepTarget && !other.needsBreakpointRequest() &&
            method.manager.areElementsEquivalent(method, other.method) && other.ordinal > target.ordinal) {
          other.ordinal--
        }
      }
    }
    return SearchResult(reorderWithSteppingFilters(selectedTargets), SmartStepIntoDetectionStatus.SUCCESS)
  }

  private class SearchResult(
    val targets: List<SmartStepTarget>,
    val status: SmartStepIntoDetectionStatus,
    val error: JvmSmartStepIntoErrorReporter.SourceInfo? = null,
  )

  /** A PSI plan whose branches can be evaluated on the debugger manager thread without a read lock. */
  private class JavaSmartStepIntoPlan private constructor(
    private val targets: List<TargetDescription>,
    private val root: Sequence,
    val lines: Set<Int>,
    private val sourceLines: Range<Int>,
    val element: PsiElement,
    val methods: Map<PsiMethod, JvmMethodMatcher.MethodInfo>,
    private val document: Document,
    private val documentStamp: Long,
    private val psiStamp: Long,
  ) {
    @RequiresReadLock
    fun isUpToDate(project: Project): Boolean =
      document.modificationStamp == documentStamp && PsiModificationTracker.getInstance(project).modificationCount == psiStamp

    @RequiresReadLockAbsence
    fun evaluate(context: EvaluationContextImpl?): BooleanArray {
      DebuggerManagerThreadImpl.assertIsManagerThread()
      val result = BooleanArray(targets.size)
      root.evaluate(context?.withAutoLoadClasses(false), result)
      return result
    }

    @RequiresReadLock
    fun createTargets(selection: BooleanArray): TargetLists {
      val matchingTargets = ArrayList<SmartStepTarget>()
      val selectedTargets = ArrayList<SmartStepTarget>()
      for ([id, immediate, factory] in targets) {
        if (!selection[id] && !immediate) continue
        val target = factory()
        target.callingExpressionLines = sourceLines
        matchingTargets.add(target)
        if (selection[id]) selectedTargets.add(target)
      }
      return TargetLists(matchingTargets, selectedTargets)
    }

    /**
     * Both lists share the same target instances.
     *
     * @property matchingTargets includes immediate calls from unselected branches to preserve the ordinals for bytecode matching
     * @property selectedTargets contains targets from selected branches; filtering removes targets and adjusts the ordinals for stepping
     */
    data class TargetLists(val matchingTargets: List<SmartStepTarget>, val selectedTargets: MutableList<SmartStepTarget>)

    private data class TargetDescription(
      val id: Int,
      val immediate: Boolean,
      val factory: () -> SmartStepTarget,
    ) : Node {
      override fun evaluate(context: EvaluationContextImpl?, selection: BooleanArray) {
        selection[id] = true
      }
    }

    private sealed interface Node {
      fun evaluate(context: EvaluationContextImpl?, selection: BooleanArray)
    }

    private data class Sequence(val nodes: List<Node>) : Node {
      override fun evaluate(context: EvaluationContextImpl?, selection: BooleanArray) {
        for (node in nodes) {
          ProgressManager.checkCanceled()
          node.evaluate(context, selection)
        }
      }
    }

    private data class Conditional(
      val evaluator: ExpressionEvaluator?,
      val thenBranch: Sequence,
      val elseBranch: Sequence,
    ) : Node {
      override fun evaluate(context: EvaluationContextImpl?, selection: BooleanArray) {
        var value = ThreeState.UNSURE
        if (evaluator != null && context != null) {
          try {
            value = ThreeState.fromBoolean(DebuggerUtilsEx.evaluateBoolean(evaluator, context))
          }
          catch (e: EvaluateException) {
            LOG.info(e)
          }
        }
        when (value) {
          ThreeState.YES -> thenBranch.evaluate(context, selection)
          ThreeState.NO -> elseBranch.evaluate(context, selection)
          ThreeState.UNSURE -> {
            thenBranch.evaluate(context, selection)
            elseBranch.evaluate(context, selection)
          }
        }
      }
    }

    private class Builder(
      private val position: SourcePosition,
      private val project: Project,
      private val lineRange: TextRange,
    ) : JavaRecursiveElementVisitor() {
      private val targets = ArrayList<TargetDescription>()
      private val nodes = ArrayList<Node>()
      private val methods = ArrayList<MethodCalls>()
      private val methodInfo = HashMap<PsiMethod, JvmMethodMatcher.MethodInfo>()
      private var textRange = lineRange
      private val contextStack = ArrayDeque<PsiMethod>()
      private val paramNameStack = ArrayDeque<String>()
      private var nextLambdaExpressionOrdinal = 0
      private var nextTargetId = 0
      private var currentNodes: MutableList<Node> = nodes

      @RequiresReadLock
      fun build(element: PsiElement, document: Document, mapping: LineNumbersMapping?): JavaSmartStepIntoPlan? {
        element.accept(this)
        var sibling = element.nextSibling
        while (sibling != null) {
          if (!lineRange.intersects(sibling.textRange)) break
          sibling.accept(this)
          sibling = sibling.nextSibling
        }
        if (targets.isEmpty()) {
          logSmartStepIntoTargetsDetection(project, Engine.JAVA, SmartStepIntoDetectionStatus.NO_TARGETS)
          return null
        }

        val sourceLines = Range(document.getLineNumber(textRange.startOffset), document.getLineNumber(textRange.endOffset))
        val lines = (sourceLines.from..sourceLines.to).mapNotNullTo(HashSet()) { sourceLine ->
          if (mapping == null) sourceLine else (mapping.sourceToBytecode(sourceLine + 1) - 1).takeIf { it >= 0 }
        }
        return JavaSmartStepIntoPlan(targets.toList(), Sequence(nodes.toList()), lines.toSet(), sourceLines,
                                     element, methodInfo.toMap(), document, document.modificationStamp,
                                     PsiModificationTracker.getInstance(project).modificationCount)
      }

      private fun methodTarget(
        method: PsiMethod,
        label: String?,
        highlightElement: PsiElement?,
        needsBreakpoint: Boolean,
        countOrdinal: Boolean,
      ): TargetDescription {
        val ordinal = if (countOrdinal) {
          val calls = methods.firstOrNull { method.manager.areElementsEquivalent(method, it.method) }
                      ?: MethodCalls(method).also { methods.add(it) }
          val ordinal = calls.count
          if (!needsBreakpoint) calls.count++
          ordinal
        }
        else 0
        methodInfo.computeIfAbsent(method, JvmMethodMatcher::getMethodInfo)
        val target = TargetDescription(nextTargetId++, !needsBreakpoint) {
          MethodSmartStepTarget(method, label, highlightElement, needsBreakpoint, null).also { it.ordinal = ordinal }
        }
        currentNodes.add(target)
        return target
      }

      private val currentParamName: String?
        get() = paramNameStack.firstOrNull()

      override fun visitAnonymousClass(aClass: PsiAnonymousClass) {
        if (!matchLine(aClass)) return
        aClass.argumentList?.accept(this)
        for (method in aClass.methods) {
          if (isSteppableMethod(method)) {
            targets.addFirst(methodTarget(method, currentParamName, method.body, needsBreakpoint = true, countOrdinal = false))
          }
        }
      }

      override fun visitLambdaExpression(expression: PsiLambdaExpression) {
        super.visitLambdaExpression(expression)
        if (!matchLine(expression)) return
        val label = currentParamName
        val body = expression.body
        val lambdaOrdinal = nextLambdaExpressionOrdinal++
        val async = !isInsideLambda(expression)
        val target = TargetDescription(nextTargetId++, false) {
          LambdaSmartStepTarget(expression, label, body, lambdaOrdinal, null, async)
        }
        currentNodes.add(target)
        targets.addFirst(target)
      }

      override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
        val resolved = expression.resolve()
        if (matchLine(expression) && resolved is PsiMethod) {
          val method = resolved.navigationElement
          if (method is PsiMethod && isSteppableMethod(method)) {
            targets.addFirst(methodTarget(method, null, expression, needsBreakpoint = true, countOrdinal = false))
          }
        }
      }

      override fun visitField(field: PsiField) {
        if (checkTextRange(field, true)) super.visitField(field)
      }

      override fun visitMethod(method: PsiMethod) {
        if (checkTextRange(method, false)) super.visitMethod(method)
      }

      override fun visitStatement(statement: PsiStatement) {
        if (checkTextRange(statement, true)) super.visitStatement(statement)
      }

      override fun visitIfStatement(statement: PsiIfStatement) {
        visitConditional(statement.condition, statement.thenBranch, statement.elseBranch)
      }

      override fun visitConditionalExpression(expression: PsiConditionalExpression) {
        visitConditional(expression.condition, expression.thenExpression, expression.elseExpression)
      }

      private fun visitConditional(condition: PsiElement?, thenBranch: PsiElement?, elseBranch: PsiElement?) {
        if (condition != null && checkTextRange(condition, true)) condition.accept(this)
        val constantValue = condition?.let { JavaPsiFacade.getInstance(project).constantEvaluationHelper.computeConstantExpression(it) }
        if (constantValue is Boolean) {
          val branch = if (constantValue) thenBranch else elseBranch
          if (branch != null && checkTextRange(branch, true)) branch.accept(this)
          return
        }
        val evaluator = if (condition != null && !DebuggerUtils.hasSideEffects(condition)) {
          try {
            EvaluatorBuilderImpl.getInstance().build(condition, position)
          }
          catch (e: EvaluateException) {
            LOG.info(e)
            null
          }
        }
        else null
        val thenNodes = buildBranch(thenBranch)
        val elseNodes = buildBranch(elseBranch)
        currentNodes.add(Conditional(evaluator, thenNodes, elseNodes))
      }

      private fun buildBranch(branch: PsiElement?): Sequence {
        val previousNodes = currentNodes
        currentNodes = ArrayList()
        try {
          if (branch != null && checkTextRange(branch, true)) branch.accept(this)
          return Sequence(currentNodes.toList())
        }
        finally {
          currentNodes = previousNodes
        }
      }

      override fun visitExpression(expression: PsiExpression) {
        checkTextRange(expression, true)
        super.visitExpression(expression)
      }

      private fun checkTextRange(expression: PsiElement, expand: Boolean): Boolean {
        val range = expression.textRange
        if (lineRange.intersects(range)) {
          if (expand) {
            // only expand to the bottom of the file
            val delta = range.endOffset - textRange.endOffset
            if (delta > 0) textRange = textRange.grown(delta)
          }
          return true
        }
        return false
      }

      private fun matchLine(element: PsiElement): Boolean = lineRange.startOffset <= element.textRange.startOffset

      override fun visitExpressionList(expressionList: PsiExpressionList) {
        visitArguments(expressionList, contextStack.firstOrNull())
      }

      private fun visitArguments(expressionList: PsiExpressionList, method: PsiMethod?) {
        if (method == null) {
          super.visitExpressionList(expressionList)
          return
        }
        val methodName = method.name
        val expressions = expressionList.expressions
        val parameters = method.parameterList.parameters
        for ([index, expression] in expressions.withIndex()) {
          if (!matchLine(expression)) continue
          val parameter = parameters.getOrNull(index)
          val paramName = if (parameter != null && !parameter.isVarArgs) parameter.name else "arg${index + 1}"
          paramNameStack.addFirst("$methodName: $paramName.")
          try {
            expression.accept(this)
          }
          finally {
            paramNameStack.removeFirst()
          }
        }
      }

      override fun visitCallExpression(expression: PsiCallExpression) {
        // Always move the outermost item in the group to the top.
        val pos = if (contextStack.isEmpty()) targets.size else -1
        val method = expression.resolveMethod()
        if (expression is PsiMethodCallExpression) {
          expression.methodExpression.qualifierExpression?.accept(this)
          visitArguments(expression.argumentList, method)
        }
        if (method != null) contextStack.addFirst(method)
        try {
          val callExpression = when (expression) {
            is PsiMethodCallExpression -> expression.methodExpression.referenceNameElement
            is PsiNewExpression -> expression.classOrAnonymousClassReference
            else -> expression
          }
          if (method != null && isSteppableMethod(method) && (callExpression == null || matchLine(callExpression))) {
            val needsBreakpoint = isInsideLambda(expression) || (expression is PsiNewExpression && expression.anonymousClass != null)
            val targetMethod = if (needsBreakpoint) method else getInvocationMethod(method, callExpression)
            val target = methodTarget(targetMethod, null, callExpression, needsBreakpoint = needsBreakpoint, countOrdinal = true)
            if (pos != -1) targets.add(pos, target) else targets.add(target)
          }
          if (expression is PsiMethodCallExpression) {
            checkTextRange(expression, true)
          }
          else {
            super.visitCallExpression(expression)
          }
        }
        finally {
          if (method != null) contextStack.removeFirst()
        }
      }

      private fun isInsideLambda(element: PsiElement): Boolean =
        PsiTreeUtil.getParentOfType(element, PsiLambdaExpression::class.java) != null

      private fun isSteppableMethod(method: PsiMethod): Boolean =
        !method.hasModifierProperty(PsiModifier.NATIVE) || PsiUtil.canBeOverridden(method)

      private fun getInvocationMethod(method: PsiMethod, highlightElement: PsiElement?): PsiMethod {
        val initializer = method.containingClass as? PsiEnumConstantInitializer ?: return method
        val reference = highlightElement?.parent as? PsiReferenceExpression ?: return method
        val qualifier = reference.qualifierExpression as? PsiReferenceExpression ?: return method
        if (qualifier.resolve() is PsiEnumConstant) {
          return method.findSuperMethods(initializer.containingClass).singleOrNull() ?: method
        }
        return method
      }

      private class MethodCalls(val method: PsiMethod, var count: Int = 0)
    }

    companion object {
      private val LOG = Logger.getInstance(JavaSmartStepIntoPlan::class.java)

      @RequiresReadLock
      fun build(position: SourcePosition, project: Project): JavaSmartStepIntoPlan? {
        val line = position.line
        if (line < 0) {
          logSmartStepIntoTargetsDetection(project, Engine.JAVA, SmartStepIntoDetectionStatus.INVALID_POSITION)
          return null // the document has been changed
        }

        val file = position.file
        val vFile = file.virtualFile
        if (vFile == null) {
          logSmartStepIntoTargetsDetection(project, Engine.JAVA, SmartStepIntoDetectionStatus.INVALID_POSITION)
          // the file is not physical
          return null
        }

        val doc = FileDocumentManager.getInstance().getDocument(vFile)
        if (doc == null || line >= doc.lineCount) {
          logSmartStepIntoTargetsDetection(project, Engine.JAVA, SmartStepIntoDetectionStatus.INVALID_POSITION)
          return null // the document has been changed
        }
        val curLineRange = DocumentUtil.getLineTextRange(doc, line)
        var element = position.elementAt
        val body = DebuggerUtilsEx.getBody(DebuggerUtilsEx.getContainingMethod(element))
        val lineRange = if (body != null) curLineRange.intersection(body.textRange) else curLineRange

        if (lineRange == null || lineRange.isEmpty || element == null || element is PsiCompiledElement) {
          logSmartStepIntoTargetsDetection(project, Engine.JAVA, SmartStepIntoDetectionStatus.INVALID_POSITION)
          return null
        }

        val initial = element
        element = getTopmostParentAfterOffset(element, lineRange.startOffset)

        val statementParent = PsiTreeUtil.getNonStrictParentOfType(initial, PsiStatement::class.java, PsiField::class.java)
        if (statementParent != null &&
            (body == null || body.textRange.contains(statementParent.textRange)) &&
            // take only wider statements
            statementParent.textRange.contains(element.textRange)) {
          element = statementParent
        }

        val mapping = vFile.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY)
        return Builder(position, project, lineRange).build(element, doc, mapping)
      }

      /** Finds the topmost parent element whose range starts after the target offset. */
      private fun getTopmostParentAfterOffset(element: PsiElement, offset: Int): PsiElement {
        var result = element
        while (true) {
          val parent = result.parent
          if (parent == null || parent.textRange.startOffset < offset) return result
          result = parent
        }
      }
    }
  }
}
