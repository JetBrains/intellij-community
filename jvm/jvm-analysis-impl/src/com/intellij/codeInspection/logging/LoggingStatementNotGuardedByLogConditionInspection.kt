// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.options.JavaClassValidator
import com.intellij.codeInsight.options.JavaIdentifierValidator
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.logging.LoggingUtil.LimitLevelType
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.registerUProblem
import com.intellij.lang.java.JavaLanguage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.LanguageConstantExpressionEvaluator
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.psiutils.CommentTracker
import com.siyeh.ig.psiutils.JavaLoggingUtils
import org.jetbrains.uast.*
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class LoggingStatementNotGuardedByLogConditionInspection : AbstractBaseUastLocalInspectionTool() {

  //for backward compatibility
  @JvmField
  var customLogMethodNameList: MutableList<String?> = mutableListOf("fine", "finer", "finest")

  //for backward compatibility
  @JvmField
  var customLogConditionMethodNameList: MutableList<String?> = mutableListOf(
    "isLoggable(java.util.logging.Level.FINE)",
    "isLoggable(java.util.logging.Level.FINER)",
    "isLoggable(java.util.logging.Level.FINEST)",
  )

  //for backward compatibility
  @JvmField
  var customLoggerClassName: String = JavaLoggingUtils.JAVA_LOGGING

  @JvmField
  var myLimitLevelType: LimitLevelType = LimitLevelType.DEBUG_AND_LOWER

  @JvmField
  var flagUnguardedConstant: Boolean = false

  override fun getOptionsPane(): OptPane {
    return OptPane.pane(
      OptPane.dropdown(
        "myLimitLevelType",
        JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.warn.on.label"),
        OptPane.option(LimitLevelType.ALL,
                       JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.all.levels.option")),
        OptPane.option(LimitLevelType.WARN_AND_LOWER,
                       JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.warn.level.and.lower.option")),
        OptPane.option(LimitLevelType.INFO_AND_LOWER,
                       JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.info.level.and.lower.option")),
        OptPane.option(LimitLevelType.DEBUG_AND_LOWER,
                       JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.debug.level.and.lower.option")),
        OptPane.option(LimitLevelType.TRACE,
                       JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.trace.level.option")),
      ),
      OptPane.checkbox("flagUnguardedConstant", JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.unguarded.constant.option"))
        .description(JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.unguarded.constant.option.comment")),

      OptPane.string("customLoggerClassName", JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.logger.name.option"),
                     JavaClassValidator())
        .description(JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.logger.name.option.comment")),
      OptPane.table("",
                    OptPane.column("customLogMethodNameList", JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.log.method.name"), JavaIdentifierValidator()),
                    OptPane.column("customLogConditionMethodNameList", JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.log.condition.text")))
        .description(JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.custom.table")),
    )
  }


  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    UastHintedVisitorAdapter.create(
      holder.file.language,
      LogStatementNotGuardedByLogConditionVisitor(holder, isOnTheFly),
      arrayOf(UCallExpression::class.java),
      directOnly = true
    )

  inner class LogStatementNotGuardedByLogConditionVisitor(
    private val holder: ProblemsHolder,
    private val isOnTheFly: Boolean,
  ) : AbstractUastNonRecursiveVisitor() {
    override fun visitCallExpression(node: UCallExpression): Boolean {
      val isCustom: Boolean
      val sourcePsi = node.sourcePsi
      if (sourcePsi == null) return true
      if (LoggingUtil.LOG_MATCHERS_WITHOUT_BUILDERS.uCallMatches(node)) {
        isCustom = false
      }
      else if (node.isMethodNameOneOf(customLogMethodNameList.filterNotNull())) {
        val uMethod = node.resolveToUElementOfType<UMethod>() ?: return true
        if (uMethod.getContainingUClass()?.qualifiedName != customLoggerClassName) {
          return true
        }
        isCustom = true
      }
      else {
        return true
      }

      //custom settings are supported only for java only for backward compatibility
      if (isCustom && node.lang != JavaLanguage.INSTANCE) {
        return true
      }

      val isInformationLevel = isOnTheFly && InspectionProjectProfileManager.isInformationLevel(getShortName(), sourcePsi)
      if (!isInformationLevel && !isCustom && LoggingUtil.skipAccordingLevel(node, myLimitLevelType)) {
        return true
      }

      if (isSurroundedByLogGuard(node, isCustom)) {
        return true
      }

      if (!isInformationLevel && skipIfOnlyConstantArguments(node)) return true

      val message = JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.log.problem.descriptor")

      val loggerLevel = LoggingUtil.getLoggerLevel(node)

      val qualifiedExpression = if (node.uastParent is UQualifiedReferenceExpression) node.uastParent else node
      var psiElement = qualifiedExpression?.sourcePsi
      while (psiElement?.parent.toUElement() == qualifiedExpression) {
        psiElement = psiElement?.parent
      }
      val before = PsiTreeUtil.skipWhitespacesAndCommentsBackward(psiElement).toUElement() as? UQualifiedReferenceExpression
      val beforeCall = before.getUCallExpression(2)
      val beforeLoggerLevel = LoggingUtil.getLoggerLevel(beforeCall)
      if (beforeCall != null &&
          (LoggingUtil.LOG_MATCHERS_WITHOUT_BUILDERS.uCallMatches(beforeCall) || beforeCall.isMethodNameOneOf(customLogMethodNameList.filterNotNull()))
      ) {
        val receiverText = node.receiver?.sourcePsi?.text
        if (receiverText != null && beforeCall.receiver?.sourcePsi?.text == receiverText &&
            !(skipIfOnlyConstantArguments(beforeCall)) &&
            beforeCall.methodName == node.methodName &&
            beforeLoggerLevel == loggerLevel) {
          return true
        }
      }

      var parent = node.uastParent
      if (parent is UQualifiedReferenceExpression) {
        parent = parent.uastParent
      }

      if (parent is ULambdaExpression || parent is UReturnExpression) {
        return true
      }

      val textCustomCondition = if (isCustom) {
        val indexOfMethod = customLogMethodNameList.indexOf(node.methodName)
        if (indexOfMethod == -1) return false
        customLogConditionMethodNameList[indexOfMethod]
      }
      else {
        null
      }
      if (node.uastParent is UQualifiedReferenceExpression) {
        holder.registerUProblem(node.uastParent as UExpression, message, CreateGuardFix(textCustomCondition))
      }
      else {
        holder.registerUProblem(node, message, CreateGuardFix(textCustomCondition))
      }
      return true
    }

    private fun skipIfOnlyConstantArguments(node: UCallExpression): Boolean {
      val arguments = node.valueArguments
      if (arguments.isEmpty()) {
        return true
      }

      val expressionEvaluator = LanguageConstantExpressionEvaluator.INSTANCE.forLanguage(node.lang)

      if (expressionEvaluator != null && !flagUnguardedConstant) {
        var constant = true
        for (parenArgument in arguments) {
          val argument = parenArgument.skipParenthesizedExprDown()
          if (argument is ULambdaExpression) {
            continue
          }

          val sourcePsi = argument.sourcePsi
          if (isConstantOrPolyadicWithConstants(argument)) {
            continue
          }
          if (sourcePsi != null && expressionEvaluator.computeConstantExpression(sourcePsi, false) == null) {
            constant = false
            break
          }
        }
        if (constant) {
          return true
        }
      }
      return false
    }

    private fun isConstantOrPolyadicWithConstants(argument: UExpression): Boolean {
      if (argument is ULiteralExpression) return true
      if (argument is UBinaryExpression && argument.operands.all { isConstantOrPolyadicWithConstants(it) }) return true
      if (argument is UPolyadicExpression && argument.operands.all { isConstantOrPolyadicWithConstants(it) }) return true
      return false
    }

    private fun isSurroundedByLogGuard(callExpression: UCallExpression, isCustom: Boolean): Boolean {
      val guardedCondition = LoggingUtil.getGuardedCondition(callExpression) ?: return false
      if (isCustom) {
        val indexOfMethod = customLogMethodNameList.indexOf(callExpression.methodName)
        if (indexOfMethod == -1) return false
        val text = customLogConditionMethodNameList[indexOfMethod]
        val expectedText = callExpression.receiver.toString() + "." + text
        return guardedCondition.sourcePsi?.textMatches(expectedText) ?: return true
      }
      val loggerLevel = LoggingUtil.getLoggerLevel(callExpression) ?: return true
      val levelFromCondition = LoggingUtil.getLevelFromCondition(guardedCondition) ?: return true
      return LoggingUtil.isGuardedIn(levelFromCondition, loggerLevel)
    }
  }

  private inner class CreateGuardFix(private val textCustomCondition: String?) : PsiUpdateModCommandQuickFix() {

    override fun getFamilyName(): String {
      return JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.log.fix.family.name")
    }

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
      val uCallExpression: UCallExpression = element.toUElement().getUCallExpression(2) ?: return

      if (textCustomCondition != null) {
        generateCustomForJava(uCallExpression)
        return
      }

      val qualifiedExpression = if (uCallExpression.uastParent is UQualifiedReferenceExpression) uCallExpression.uastParent else uCallExpression
      if (qualifiedExpression !is UExpression) return

      var currentElement = qualifiedExpression.sourcePsi
      while (currentElement?.parent.toUElement() == qualifiedExpression) {
        currentElement = currentElement?.parent
      }

      if (currentElement == null) return

      val calls = mutableListOf<UExpression>()
      calls.add(qualifiedExpression)
      val receiverText = uCallExpression.receiver?.sourcePsi?.text ?: return

      val loggerLevel = LoggingUtil.getLoggerLevel(uCallExpression) ?: return

      while (true) {
        val nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(currentElement)
        val after = nextElement.toUElement() as? UQualifiedReferenceExpression
        val afterCall = after.getUCallExpression(2) ?: break
        val afterLevel = LoggingUtil.getLoggerLevel(afterCall)
        if (after != null &&
            (LoggingUtil.LOG_MATCHERS_WITHOUT_BUILDERS.uCallMatches(afterCall) || afterCall.isMethodNameOneOf(customLogMethodNameList.filterNotNull())) &&
            receiverText == after.receiver.sourcePsi?.text &&
            uCallExpression.methodName == afterCall.methodName &&
            afterLevel == loggerLevel) {
          calls.add(after)
          currentElement = nextElement
          continue
        }
        break
      }
      val conditionCall = LoggingUtil.GUARD_MAP.entries.firstOrNull { it.value == loggerLevel.name.lowercase() }?.key ?: return

      val elementFactory = uCallExpression.getUastElementFactory(project) ?: return
      if (calls.isEmpty()) return
      var condition: UExpression =
        elementFactory.createQualifiedReference(qualifiedName = "${receiverText}.$conditionCall",
                                                context = uCallExpression.sourcePsi) ?: return

      if (condition.uastParent is UQualifiedReferenceExpression) {
        condition = condition.uastParent as UQualifiedReferenceExpression
      }
      val blockExpression = elementFactory.createBlockExpression(calls, uCallExpression.sourcePsi) ?: return

      val ifExpression = elementFactory.createIfExpression(condition, blockExpression, null, element) ?: return

      val uExpression = calls[0]
      val newIfExpression = uExpression.replace(ifExpression)
      val newCondition = newIfExpression?.condition
      if (newCondition is UQualifiedReferenceExpression && newCondition.resolveToUElement() !is UMethod) {
        val callCondition = elementFactory.createCallExpression(newCondition.receiver, conditionCall, emptyList(), PsiTypes.booleanType(), UastCallKind.METHOD_CALL, newCondition.sourcePsi)
        if (callCondition != null) {
          newCondition.replace(callCondition)
        }
      }

      if (calls.size > 1) {
        for (i in 1 until calls.size) {
          calls[i].sourcePsi?.delete()
        }
      }
    }

    /**
     * It is only used to support backward compatibility with the previous java inspections.
     * The main generation is for UAST
     */
    private fun generateCustomForJava(uCallExpression: UExpression) {
      val methodCallExpression = uCallExpression.javaPsi
      if (methodCallExpression !is PsiMethodCallExpression) {
        return
      }
      val statement = PsiTreeUtil.getParentOfType(methodCallExpression, PsiStatement::class.java) ?: return
      val logStatements = mutableListOf<PsiStatement>()
      logStatements.add(statement)
      val methodExpression = methodCallExpression.getMethodExpression()
      var nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement::class.java)
      while (isSameLogMethodCall(nextStatement, methodExpression)) {
        if (nextStatement != null) {
          logStatements.add(nextStatement)
          nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement::class.java)
        }
        else {
          break
        }
      }
      val factory = JavaPsiFacade.getInstance(methodExpression.project).elementFactory
      val qualifier = methodExpression.qualifierExpression
      if (qualifier == null) {
        return
      }
      val ifStatementText = "if (" + qualifier.text + '.' + textCustomCondition + ") {}"
      val ifStatement = factory.createStatementFromText(ifStatementText, statement) as PsiIfStatement
      val blockStatement = (ifStatement.thenBranch as PsiBlockStatement?) ?: return
      val codeBlock = blockStatement.codeBlock
      for (logStatement in logStatements) {
        codeBlock.add(logStatement)
      }
      val firstStatement = logStatements[0]
      val parent = firstStatement.parent
      val codeStyleManager = JavaCodeStyleManager.getInstance(methodCallExpression.project)
      if (parent is PsiIfStatement && parent.elseBranch != null) {
        val newBlockStatement = factory.createStatementFromText("{}", statement) as PsiBlockStatement
        newBlockStatement.codeBlock.add(ifStatement)
        val commentTracker = CommentTracker()
        val result = commentTracker.replace(firstStatement, newBlockStatement)
        codeStyleManager.shortenClassReferences(result)
        return
      }
      val result = parent.addBefore(ifStatement, firstStatement)
      codeStyleManager.shortenClassReferences(result)
      for (logStatement in logStatements) {
        logStatement.delete()
      }
    }

    private fun isSameLogMethodCall(current: PsiStatement?, targetReference: PsiReferenceExpression): Boolean {
      if (current == null) {
        return false
      }
      if (current !is PsiExpressionStatement) {
        return false
      }
      val expression = current.expression
      if (expression !is PsiMethodCallExpression) {
        return false
      }
      val methodExpression = expression.methodExpression
      val referenceName = methodExpression.referenceName
      if (targetReference.referenceName != referenceName) {
        return false
      }
      val qualifier = methodExpression.qualifierExpression
      val qualifierText = qualifier?.text
      return qualifierText != null && qualifierText == targetReference.qualifierExpression?.text
    }
  }
}