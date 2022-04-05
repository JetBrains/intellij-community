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
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.SmartList
import com.siyeh.ig.junit.getPublicStaticErrorMessage
import org.jetbrains.annotations.PropertyKey
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
    checkDeclaration(method, manager, isOnTheFly,
                     modifierMessage ="jvm.inspections.junit.rule.problem.method.descriptor",
                     ruleTypeMessage = "jvm.inspections.junit.rule.type.problem.method.descriptor",
                     classRuleTypeMessage = "jvm.inspections.junit.class.rule.type.problem.method.descriptor"
    )

  override fun checkField(field: UField, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> =
    checkDeclaration(field, manager, isOnTheFly,
                     modifierMessage = "jvm.inspections.junit.rule.problem.field.descriptor",
                     ruleTypeMessage = "jvm.inspections.junit.rule.type.problem.field.descriptor",
                     classRuleTypeMessage = "jvm.inspections.junit.class.rule.type.problem.field.descriptor"
    )

  private fun checkDeclaration(
    uDeclaration: UDeclaration,
    manager: InspectionManager,
    isOnTheFly: Boolean,
    modifierMessage: @PropertyKey(resourceBundle = JvmAnalysisBundle.BUNDLE) String,
    ruleTypeMessage: @PropertyKey(resourceBundle = JvmAnalysisBundle.BUNDLE) String,
    classRuleTypeMessage: @PropertyKey(resourceBundle = JvmAnalysisBundle.BUNDLE) String,
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
      val message = getPublicStaticErrorMessage(isStatic, isPublic, classRuleAnnotated)
      if (message.isNotEmpty()) {
        val actions = SmartList<IntentionAction>()
        if (ruleAnnotated && isStatic) actions.addAll(createModifierActions(uDeclaration, modifierRequest(JvmModifier.STATIC, false)))
        if (classRuleAnnotated && !isStatic) {
          actions.addAll(createModifierActions(uDeclaration, modifierRequest(JvmModifier.STATIC, true)))
        }
        actions.addAll(createModifierActions(uDeclaration, modifierRequest(JvmModifier.PUBLIC, true)))
        val intention = IntentionWrapper.wrapToQuickFixes(actions, sourcePsi.containingFile).toTypedArray()
        val problem = manager.createProblemDescriptor(
          nameIdentifier,
          JvmAnalysisBundle.message(modifierMessage, if (ruleAnnotated) RULE_FQN else CLASS_RULE_FQN, message),
          isOnTheFly,
          intention,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING
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
      val isTestRuleInheritor = InheritanceUtil.isInheritor(aClass, false, TEST_RULE)
      if (isTestRuleInheritor) return problems.toTypedArray()
      val isMethodRuleInheritor = InheritanceUtil.isInheritor(aClass, false, METHOD_RULE)
      val typeErrorMessage = when {
        ruleAnnotated && !isMethodRuleInheritor -> JvmAnalysisBundle.message(classRuleTypeMessage, TEST_RULE, METHOD_RULE)
        classRuleAnnotated-> JvmAnalysisBundle.message(ruleTypeMessage, TEST_RULE)
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

  companion object {
    const val RULE_FQN = "org.junit.Rule"

    const val CLASS_RULE_FQN = "org.junit.ClassRule"

    const val TEST_RULE = "org.junit.rules.TestRule"

    const val METHOD_RULE = "org.junit.rules.MethodRule"
  }
}