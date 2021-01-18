// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.intention.IntentionAction
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
import org.jetbrains.uast.UField
import org.jetbrains.uast.UastVisibility
import org.jetbrains.uast.sourcePsiElement
import javax.swing.JComponent

class JUnitRuleInspection : AbstractBaseUastLocalInspectionTool() {
  var REPORT_RULE_PROBLEMS = true
  var REPORT_CLASS_RULE_PROBLEMS = true

  override fun createOptionsPanel(): JComponent = MultipleCheckboxOptionsPanel(this).apply {
    addCheckbox(JavaAnalysisBundle.message("junit.rule.rule.option"), "REPORT_RULE_PROBLEMS")
    addCheckbox(JavaAnalysisBundle.message("junit.rule.classrule.option"), "REPORT_CLASS_RULE_PROBLEMS")
  }

  override fun checkField(field: UField, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val sourcePsi = field.sourcePsi ?: return emptyArray()
    val nameIdentifier = field.uastAnchor.sourcePsiElement ?: return emptyArray()
    val javaPsi = field.javaPsi
    if (javaPsi !is PsiModifierListOwner) return emptyArray()
    val ruleAnnotated = REPORT_RULE_PROBLEMS && AnnotationUtil.isAnnotated(javaPsi, RULE_FQN, 0)
    val classRuleAnnotated = REPORT_CLASS_RULE_PROBLEMS && AnnotationUtil.isAnnotated(javaPsi, CLASS_RULE_FQN, 0)
    val problems = SmartList<ProblemDescriptor>()
    if (ruleAnnotated || classRuleAnnotated) {
      val isStatic = field.isStatic
      val isPublic = field.visibility == UastVisibility.PUBLIC
      val message = getPublicStaticErrorMessage(isStatic, isPublic, classRuleAnnotated)
      if (message.isNotEmpty()) {
        val actions = SmartList<IntentionAction>()
        if (ruleAnnotated && isStatic) actions.addAll(createModifierActions(field, modifierRequest(JvmModifier.STATIC, false)))
        if (classRuleAnnotated && !isStatic) {
          actions.addAll(createModifierActions(field, modifierRequest(JvmModifier.STATIC, true)))
        }
        actions.addAll(field.createMakePublicActions())
        val intention = IntentionWrapper.wrapToQuickFixes(actions, sourcePsi.containingFile).toTypedArray()
        val problem = manager.createProblemDescriptor(
          nameIdentifier,
          JvmAnalysisBundle.message(
            "jvm.inspections.junit.rule.problem.descriptor", if (ruleAnnotated) RULE_FQN else CLASS_RULE_FQN, message
          ),
          isOnTheFly,
          intention,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        )
        problems.add(problem)
      }

      if (classRuleAnnotated) {
        val actions = SmartList<IntentionAction>()
        val aClass = PsiUtil.resolveClassInClassTypeOnly(field.type)
        if (!InheritanceUtil.isInheritor(aClass, false, "org.junit.rules.TestRule") &&
            !InheritanceUtil.isInheritor(aClass, false, "org.junit.rules.MethodRule")
        ) {
          val intention = IntentionWrapper.wrapToQuickFixes(actions, sourcePsi.containingFile).toTypedArray()
          val problem = manager.createProblemDescriptor(
            nameIdentifier, JvmAnalysisBundle.message("jvm.inspections.junit.rule.type.problem.descriptor", CLASS_RULE_FQN), isOnTheFly,
            intention, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
          )
          problems.add(problem)
        }
      }
    }
    return problems.toTypedArray()
  }

  companion object {
    const val RULE_FQN = "org.junit.Rule"
    const val CLASS_RULE_FQN = "org.junit.ClassRule"
  }
}