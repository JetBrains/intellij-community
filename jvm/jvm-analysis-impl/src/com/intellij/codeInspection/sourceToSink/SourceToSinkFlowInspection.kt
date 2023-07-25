// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.options.JavaClassValidator
import com.intellij.codeInspection.*
import com.intellij.codeInspection.options.*
import com.intellij.codeInspection.restriction.AnnotationContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.psiutils.MethodMatcher
import org.jdom.Element
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class SourceToSinkFlowInspection : AbstractBaseUastLocalInspectionTool() {

  @JvmField
  var taintedAnnotations: MutableList<String?> = mutableListOf("javax.annotation.Tainted",
                                                               "org.checkerframework.checker.tainting.qual.Tainted")

  @JvmField
  var untaintedAnnotations: MutableList<String?> = mutableListOf("javax.annotation.Untainted",
                                                                 "org.checkerframework.checker.tainting.qual.Untainted")

  @JvmField
  var untaintedParameterIndex: MutableList<String?> = mutableListOf()

  @JvmField
  var untaintedParameterMethodClass: MutableList<String?> = mutableListOf()

  @JvmField
  var untaintedParameterMethodName: MutableList<String?> = mutableListOf()

  @JvmField
  var taintedParameterIndex: MutableList<String?> = mutableListOf()

  @JvmField
  var taintedParameterMethodClass: MutableList<String?> = mutableListOf()

  @JvmField
  var taintedParameterMethodName: MutableList<String?> = mutableListOf()

  private val myUntaintedMethodMatcher: MethodMatcher = MethodMatcher().finishDefault()

  private val myTaintedMethodMatcher: MethodMatcher = MethodMatcher(false, "myTaintedMethodMatcher").finishDefault()

  @JvmField
  var myUntaintedFieldClasses: MutableList<String?> = mutableListOf()

  @JvmField
  var myUntaintedFieldNames: MutableList<String?> = mutableListOf()

  @JvmField
  var processOuterMethodAsQualifierAndArguments: Boolean = true

  @JvmField
  var parameterOfPrivateMethodIsUntainted: Boolean = false

  @JvmField
  var warnIfComplex: Boolean = false

  @JvmField
  val skipClasses: MutableList<String?> = mutableListOf(
    "java.lang.Boolean", "boolean", "kotlin.Boolean", "java.lang.Class", "kotlin.reflect.KClass"
  )

  @JvmField
  var showUnknownString: Boolean = true

  @JvmField
  var showUnsafeString: Boolean = true

  override fun getOptionsPane(): OptPane {
    return OptPane.pane(

      OptPane.stringList("taintedAnnotations",
                         JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.tainted.annotations"),
                         JavaClassValidator().annotationsOnly()
      ).comment(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.tainted.annotations.comment")),

      OptPane.stringList("untaintedAnnotations",
                         JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.annotations"),
                         JavaClassValidator().annotationsOnly()
      ).comment(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.annotations.comment")),

      OptPane.table(
        JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.tainted.parameters"),
        OptPane.column("taintedParameterMethodClass",
                       InspectionGadgetsBundle.message("result.of.method.call.ignored.class.column.title"),
                       JavaClassValidator()),
        OptPane.column("taintedParameterMethodName",
                       InspectionGadgetsBundle.message("method.name.regex"),
                       RegexValidator()),
        OptPane.column("taintedParameterIndex",
                       JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.index.parameter"),
                       IntValidator),
      ).comment(
        JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.tainted.parameters.comment")),

      OptPane.table(
        JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.parameters"),
        OptPane.column("untaintedParameterMethodClass",
                       InspectionGadgetsBundle.message("result.of.method.call.ignored.class.column.title"),
                       JavaClassValidator()),
        OptPane.column("untaintedParameterMethodName",
                       InspectionGadgetsBundle.message("method.name.regex"),
                       RegexValidator()),
        OptPane.column("untaintedParameterIndex",
                       JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.index.parameter"),
                       IntValidator),
      ).comment(
        JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.parameters.comment")),

      OptPane.checkbox("processOuterMethodAsQualifierAndArguments",
                       JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.process.as.qualifier.arguments"))
        .comment(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.process.as.qualifier.arguments.comment")),

      myTaintedMethodMatcher.getTable(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.tainted.methods")).prefix(
        "myTaintedMethodMatcher")
        .comment(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.tainted.methods.comment")),

      myUntaintedMethodMatcher.getTable(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.methods")).prefix(
        "myUntaintedMethodMatcher")
        .comment(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.methods.comment")),

      OptPane.stringList("skipClasses", JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.safe.class"))
        .comment(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.safe.class.comment")),

      OptPane.table(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.fields"),
                    OptPane.column("myUntaintedFieldClasses",
                                   InspectionGadgetsBundle.message("result.of.method.call.ignored.class.column.title"),
                                   JavaClassValidator()),
                    OptPane.column("myUntaintedFieldNames",
                                   JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.fields.name"))
      ).comment(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.fields.comment")),

      OptPane.checkbox("parameterOfPrivateMethodIsUntainted",
                       JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.check.private.methods"))
        .comment(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.check.private.methods.comment")),

      OptPane.checkbox("warnIfComplex",
                       JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.check.warn.if.complex"))
        .comment(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.check.warn.if.complex.comment")),

      OptPane.checkbox("showUnknownString",
                       JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.show.unknown.string")),

      OptPane.checkbox("showUnsafeString",
                       JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.show.unsafe.string")),
    )
  }

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor {
    val scope = GlobalSearchScope.allScope(holder.project)
    val firstAnnotation: String? = untaintedAnnotations.firstOrNull {
      it != null && JavaPsiFacade.getInstance(holder.project).findClass(it, scope) != null
    }

    if (firstAnnotation == null && untaintedParameterIndex.size == 0) {
      return PsiElementVisitor.EMPTY_VISITOR
    }

    val configuration = UntaintedConfiguration(taintedAnnotations = taintedAnnotations,
                                               unTaintedAnnotations = untaintedAnnotations,
                                               firstAnnotation = firstAnnotation,
                                               methodClass = myUntaintedMethodMatcher.classNames,
                                               methodNames = myUntaintedMethodMatcher.methodNamePatterns,
                                               fieldClass = myUntaintedFieldClasses,
                                               fieldNames = myUntaintedFieldNames,
                                               processOuterMethodAsQualifierAndArguments = processOuterMethodAsQualifierAndArguments,
                                               processInnerMethodAsQualifierAndArguments = false,
                                               skipClasses = skipClasses,
                                               parameterOfPrivateMethodIsUntainted = parameterOfPrivateMethodIsUntainted).copy()

    val factory = TaintValueFactory(configuration).also {

      it.add(TaintValueFactory.fromMethodResult(methodNames = myTaintedMethodMatcher.methodNamePatterns,
                                                methodClass = myTaintedMethodMatcher.classNames,
                                                targetValue = TaintValue.TAINTED))

      it.add(TaintValueFactory.fromParameters(methodNames = untaintedParameterMethodName,
                                              methodClass = untaintedParameterMethodClass,
                                              methodParameterIndex = untaintedParameterIndex.map { index -> index?.toIntOrNull() },
                                              targetValue = TaintValue.UNTAINTED))

      it.add(TaintValueFactory.fromParameters(methodNames = taintedParameterMethodName,
                                              methodClass = taintedParameterMethodClass,
                                              methodParameterIndex = taintedParameterIndex.map { index -> index?.toIntOrNull() },
                                              targetValue = TaintValue.TAINTED))
    }
    return UastHintedVisitorAdapter.create(holder.file.language,
                                           SourceToSinkFlowVisitor(holder, factory, warnIfComplex, showUnknownString, showUnsafeString),
                                           arrayOf(UCallExpression::class.java,
                                                   UReturnExpression::class.java,
                                                   UBinaryExpression::class.java,
                                                   ULocalVariable::class.java,
                                                   UField::class.java,
                                                   UDeclarationsExpression::class.java,
                                                   UParameter::class.java),
                                           directOnly = true)


  }

  private class SourceToSinkFlowVisitor(
    private val holder: ProblemsHolder,
    private val factory: TaintValueFactory,
    private val warnIfComplex: Boolean,
    private val showUnknownString: Boolean,
    private val showUnsafeString: Boolean
  ) : AbstractUastNonRecursiveVisitor() {

    override fun visitCallExpression(node: UCallExpression): Boolean {
      node.valueArguments.forEach { processExpression(it) }
      return super.visitCallExpression(node)
    }

    override fun visitReturnExpression(node: UReturnExpression): Boolean {
      processExpression(node.returnExpression)
      return super.visitReturnExpression(node)
    }

    override fun visitDeclarationsExpression(node: UDeclarationsExpression): Boolean {
      node.declarations.forEach {
        when (it) {
          is UParameter -> processExpression(it.uastInitializer)
          is ULocalVariable -> processExpression(it.uastInitializer)
          is UField -> processExpression(it.uastInitializer)
        }
      }
      return super.visitDeclarationsExpression(node)
    }

    override fun visitParameter(node: UParameter): Boolean {
      processExpression(node.uastInitializer)
      return super.visitParameter(node)
    }

    override fun visitLocalVariable(node: ULocalVariable): Boolean {
      processExpression(node.uastInitializer)
      return super.visitLocalVariable(node)
    }

    override fun visitField(node: UField): Boolean {
      processExpression(node.uastInitializer)
      return super.visitField(node)
    }

    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
      if (node.operator is UastBinaryOperator.AssignOperator) {
        processExpression(node.rightOperand)
      }
      return super.visitBinaryExpression(node)
    }

    private fun processExpression(expression: UExpression?) {
      if (expression == null) return
      val expressionType: PsiType? = expression.getExpressionType()
      if (expressionType == null || !expressionType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) return
      val annotationContext = AnnotationContext.fromExpression(expression)
      val contextValue: TaintValue = factory.fromAnnotationContext(annotationContext)
      if (contextValue !== TaintValue.UNTAINTED) return
      val taintAnalyzer = TaintAnalyzer(factory)
      var taintValue = try {
        taintAnalyzer.analyzeExpression(expression, false)
      }
      catch (e: DeepTaintAnalyzerException) {
        if (warnIfComplex) {
          val errorMessage: String = JvmAnalysisBundle.message("jvm.inspections.source.to.sink.flow.too.complex")
          holder.registerUProblem(expression, errorMessage, *arrayOf(), highlightType = ProblemHighlightType.WEAK_WARNING)
        }
        return
      }
      taintValue = taintValue.join(contextValue)
      if (taintValue === TaintValue.UNTAINTED) return
      if (taintValue == TaintValue.UNKNOWN && !showUnknownString) return
      if (taintValue == TaintValue.TAINTED && !showUnsafeString) return
      val errorMessage = JvmAnalysisBundle.message(taintValue.getErrorMessage(annotationContext))
      var fixes: Array<LocalQuickFix> = arrayOf()
      val sourcePsi = expression.sourcePsi
      if (sourcePsi != null && taintValue === TaintValue.UNKNOWN &&
          taintAnalyzer.nonMarkedElements.any { it.myNonMarked != null }) {
        if (factory.getConfiguration().firstAnnotation != null) {
          fixes = arrayOf(MarkAsSafeFix(sourcePsi, factory),
                          PropagateFix(sourcePsi, factory, true))
        }
        else {
          fixes = arrayOf(PropagateFix(sourcePsi, factory, false))
        }
      }
      holder.registerUProblem(expression, errorMessage, *fixes)
    }
  }

  override fun getID(): String {
    return "tainting"
  }

  override fun readSettings(element: Element) {
    super.readSettings(element)

    myUntaintedMethodMatcher.readSettings(element)
    myTaintedMethodMatcher.readSettings(element)
  }

  override fun writeSettings(element: Element) {
    super.writeSettings(element)

    myUntaintedMethodMatcher.writeSettings(element)
    myTaintedMethodMatcher.writeSettings(element)
  }

  fun setTaintedMethod(className: String, methodName: String) {
    myTaintedMethodMatcher.add(className, methodName)
  }

  fun setUntaintedMethod(className: String, methodName: String) {
    myUntaintedMethodMatcher.add(className, methodName)
  }
}

private fun OptRegularComponent.comment(@NlsContexts.Tooltip @NlsSafe comment: String): OptRegularComponent {
  if (this is OptDescribedComponent) {
    val component = this.description(comment)
    return component ?: this
  }
  return this
}

private val IntValidator = object : StringValidator {
  override fun validatorId(): String {
    return "Int.Validator"
  }

  override fun getErrorMessage(project: Project?, string: String): String? {
    val intOrNull = string.toIntOrNull()
    if (intOrNull != null) {
      return null
    }
    return JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.not.number")
  }
}
