// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.java.analysis.JavaAnalysisBundle
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.SmartList
import org.jetbrains.uast.*
import javax.swing.JComponent

class JUnitRuleInspection : AbstractBaseUastLocalInspectionTool() {
  var REPORT_RULE_PROBLEMS = true

  var REPORT_CLASS_RULE_PROBLEMS = true

  override fun createOptionsPanel(): JComponent = MultipleCheckboxOptionsPanel(this).apply {
    addCheckbox(JavaAnalysisBundle.message("junit.rule.rule.option"), "REPORT_RULE_PROBLEMS")
    addCheckbox(JavaAnalysisBundle.message("junit.rule.classrule.option"), "REPORT_CLASS_RULE_PROBLEMS")
  }

  override fun checkMethod(method: UMethod, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> =
    checkDeclaration(method, manager, isOnTheFly, "Method", "Method return")

  override fun checkField(field: UField, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> =
    checkDeclaration(field, manager, isOnTheFly, "Field", "Field")

  private fun checkDeclaration(
    uDeclaration: UDeclaration,
    manager: InspectionManager,
    isOnTheFly: Boolean,
    memberDescription: @NlsSafe String,
    memberTypeDescription: @NlsSafe String
  ): Array<ProblemDescriptor> {
    val sourcePsi = uDeclaration.sourcePsi ?: return emptyArray()
    val nameIdentifier = uDeclaration.uastAnchor.sourcePsiElement ?: return emptyArray()
    val javaPsi = uDeclaration.javaPsi
    if (javaPsi !is PsiModifierListOwner) return emptyArray()
    val ruleAnnotated = REPORT_RULE_PROBLEMS && AnnotationUtil.isAnnotated(javaPsi, RULE_FQN, 0)
    val classRuleAnnotated = REPORT_CLASS_RULE_PROBLEMS && AnnotationUtil.isAnnotated(javaPsi, CLASS_RULE_FQN, 0)
    val problems = SmartList<ProblemDescriptor>()
    if (ruleAnnotated || classRuleAnnotated) {
      val isStatic = uDeclaration.isStatic
      val isPublic = uDeclaration.visibility == UastVisibility.PUBLIC
      val issues = getIssues(isStatic, isPublic, classRuleAnnotated)
      if (issues.isNotEmpty()) {
        val ruleFqn = if (ruleAnnotated) RULE_FQN else CLASS_RULE_FQN
        val modifierMessage = when (issues.size) {
          1 -> JvmAnalysisBundle.message(
            "jvm.inspections.junit.rule.signature.problem.single.descriptor", memberDescription, ruleFqn, issues.first()
          )
          2 -> JvmAnalysisBundle.message(
            "jvm.inspections.junit.rule.signature.problem.double.descriptor", memberDescription, ruleFqn, issues.first(), issues.last()
          )
          else -> error("Amount of issues should be smaller than 2")
        }
        val actions = SmartList<IntentionAction>()
        if (ruleAnnotated && isStatic) actions.addAll(createModifierActions(uDeclaration, modifierRequest(JvmModifier.STATIC, false)))
        if (classRuleAnnotated && !isStatic) {
          actions.addAll(createModifierActions(uDeclaration, modifierRequest(JvmModifier.STATIC, true)))
        }
        actions.addAll(createModifierActions(uDeclaration, modifierRequest(JvmModifier.PUBLIC, true)))
        val intention = IntentionWrapper.wrapToQuickFixes(actions, sourcePsi.containingFile).toTypedArray()
        val problem = manager.createProblemDescriptor(
          nameIdentifier, modifierMessage, isOnTheFly, intention, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        )
        problems.add(problem)
      }

      val actions = SmartList<IntentionAction>()
      val type = when (uDeclaration) {
        is UMethod -> uDeclaration.returnType
        is UField -> uDeclaration.type
        else -> throw IllegalStateException("Expected method or field.")
      }
      val aClass = PsiUtil.resolveClassInClassTypeOnly(type)
      val isTestRuleInheritor = InheritanceUtil.isInheritor(aClass, false, TEST_RULE_FQN)
      if (isTestRuleInheritor) return problems.toTypedArray()
      val isMethodRuleInheritor = InheritanceUtil.isInheritor(aClass, false, METHOD_RULE_FQN)
      val typeErrorMessage = when {
        ruleAnnotated && !isMethodRuleInheritor -> JvmAnalysisBundle.message(
          "jvm.inspections.junit.rule.type.problem.descriptor", memberTypeDescription, TEST_RULE_FQN, METHOD_RULE_FQN
        )
        classRuleAnnotated-> JvmAnalysisBundle.message(
          "jvm.inspections.junit.class.rule.type.problem.descriptor", memberTypeDescription, TEST_RULE_FQN
        )
        else -> null
      }
      typeErrorMessage?.let { errorMessage ->
        val quickFix = IntentionWrapper.wrapToQuickFixes(actions, sourcePsi.containingFile).toTypedArray()
        val problem = manager.createProblemDescriptor(
          nameIdentifier, errorMessage, isOnTheFly,
          quickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        )
        problems.add(problem)
      }
    }
    return problems.toTypedArray()
  }

  private fun getIssues(isStatic: Boolean, isPublic: Boolean, shouldBeStatic: Boolean): List<@NlsSafe String> = SmartList<String>().apply {
    if (!isPublic) add("'public'")
    when {
      isStatic && !shouldBeStatic -> add("non-static")
      !isStatic && shouldBeStatic -> add("'static'")
    }
  }

  companion object {
    const val RULE_FQN = "org.junit.Rule"

    const val CLASS_RULE_FQN = "org.junit.ClassRule"

    const val TEST_RULE_FQN = "org.junit.rules.TestRule"

    const val METHOD_RULE_FQN = "org.junit.rules.MethodRule"
  }
}