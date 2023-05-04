// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.options.JavaClassValidator
import com.intellij.codeInspection.*
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.restriction.AnnotationContext
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

  private val myUntaintedMethodMatcher: MethodMatcher = MethodMatcher().finishDefault()

  @JvmField
  val myUntaintedFieldClasses: MutableList<String?> = mutableListOf()

  @JvmField
  val myUntaintedFieldNames: MutableList<String?> = mutableListOf()

  @JvmField
  var processMethodAsQualifierAndArguments = true

  @JvmField
  val skipClasses: MutableList<String?> = mutableListOf(
    "java.lang.Boolean", "boolean", "kotlin.Boolean", "java.lang.Class", "kotlin.reflect.KClass"

  )

  override fun getOptionsPane(): OptPane {
    return OptPane.pane(
      OptPane.stringList("taintedAnnotations",
                         JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.tainted.annotations"),
                         JavaClassValidator().annotationsOnly()
      ),
      OptPane.stringList("untaintedAnnotations",
                         JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.annotations"),
                         JavaClassValidator().annotationsOnly()
      ),
      OptPane.checkbox("processMethodAsQualifierAndArguments",
                       JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.process.as.qualifier.arguments")),
      myUntaintedMethodMatcher.getTable(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.methods"))
        .prefix("myUntaintedMethodMatcher"),
      OptPane.stringList("skipClasses",
                         JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.safe.class"),
                         JavaClassValidator()),
      OptPane.table(JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.fields"),
                    OptPane.column("myUntaintedFieldClasses",
                                   InspectionGadgetsBundle.message("result.of.method.call.ignored.class.column.title"),
                                   JavaClassValidator()),
                    OptPane.column("myUntaintedFieldNames",
                                   JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.untainted.fields.name"))
      ))
  }

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor {
    val scope = GlobalSearchScope.allScope(holder.project)
    val firstAnnotation: String = untaintedAnnotations.firstOrNull() {
      it != null && JavaPsiFacade.getInstance(holder.project).findClass(it, scope) != null
    } ?: return PsiElementVisitor.EMPTY_VISITOR

    val configuration = UntaintedConfiguration(taintedAnnotations,
                                               untaintedAnnotations,
                                               firstAnnotation,
                                               myUntaintedMethodMatcher.classNames,
                                               myUntaintedMethodMatcher.methodNamePatterns,
                                               myUntaintedFieldClasses,
                                               myUntaintedFieldNames,
                                               processMethodAsQualifierAndArguments,
                                               skipClasses)

    return UastHintedVisitorAdapter.create(holder.file.language, SourceToSinkFlowVisitor(holder, TaintValueFactory(configuration)),
                                           arrayOf(UCallExpression::class.java,
                                                   UReturnExpression::class.java,
                                                   UBinaryExpression::class.java,
                                                   ULocalVariable::class.java,
                                                   UField::class.java),
                                           directOnly = true)


  }

  private class SourceToSinkFlowVisitor(
    private val holder: ProblemsHolder,
    private val factory: TaintValueFactory
  ) : AbstractUastNonRecursiveVisitor() {

    override fun visitCallExpression(node: UCallExpression): Boolean {
      node.valueArguments.forEach { processExpression(it) }
      return super.visitCallExpression(node)
    }

    override fun visitReturnExpression(node: UReturnExpression): Boolean {
      processExpression(node.returnExpression)
      return super.visitReturnExpression(node)
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
      val contextValue: TaintValue = factory.of(annotationContext)
      if (contextValue !== TaintValue.UNTAINTED) return
      val taintAnalyzer = TaintAnalyzer(factory)
      var taintValue = taintAnalyzer.analyzeExpression(expression, false)
      taintValue = taintValue.join(contextValue)
      if (taintValue === TaintValue.UNTAINTED) return
      val errorMessage = JvmAnalysisBundle.message(taintValue.getErrorMessage(annotationContext))
      var fixes: Array<LocalQuickFix> = arrayOf()
      val sourcePsi = expression.sourcePsi
      if (sourcePsi != null && taintValue === TaintValue.UNKNOWN &&
          taintAnalyzer.nonMarkedElements.any { it.myNonMarked != null }) {
        fixes = arrayOf(MarkAsSafeFix(sourcePsi, factory),
                        PropagateFix(sourcePsi, factory, true))
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
  }

  override fun writeSettings(element: Element) {
    super.writeSettings(element)
    myUntaintedMethodMatcher.writeSettings(element)
  }
}