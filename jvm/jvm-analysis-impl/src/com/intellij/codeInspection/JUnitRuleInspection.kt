// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.java.analysis.JavaAnalysisBundle
import com.intellij.lang.Language
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.lang.jvm.actions.annotationRequest
import com.intellij.lang.jvm.actions.createAddAnnotationActions
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.SmartList
import org.jetbrains.uast.UField
import org.jetbrains.uast.UastVisibility
import javax.swing.JComponent

class JUnitRuleInspection : AbstractBaseUastLocalInspectionTool() {
  var REPORT_RULE_PROBLEMS = true
  var REPORT_CLASS_RULE_PROBLEMS = true

  override fun createOptionsPanel(): JComponent = MultipleCheckboxOptionsPanel(this).apply {
    addCheckbox(JavaAnalysisBundle.message("junit.rule.rule.option"), "REPORT_RULE_PROBLEMS")
    addCheckbox(JavaAnalysisBundle.message("junit.rule.classrule.option"), "REPORT_CLASS_RULE_PROBLEMS")
  }

  override fun checkField(field: UField, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val sourcePsi = field.sourcePsi
    val javaPsi = field.javaPsi
    if (javaPsi !is PsiModifierListOwner) return emptyArray()
    val fieldName = (if (sourcePsi is PsiNameIdentifierOwner) sourcePsi.nameIdentifier else return emptyArray()) ?: return emptyArray()
    val ruleAnnotated = REPORT_RULE_PROBLEMS && AnnotationUtil.isAnnotated(javaPsi, RULE_FQN, 0)
    val classRuleAnnotated = REPORT_CLASS_RULE_PROBLEMS && AnnotationUtil.isAnnotated(javaPsi, CLASS_RULE_FQN, 0)
    if (!ruleAnnotated && !classRuleAnnotated) return emptyArray()
    val problems = SmartList<ProblemDescriptor>()
    val isStatic = field.isStatic
    val isPublic = field.visibility == UastVisibility.PUBLIC
    if (ruleAnnotated || classRuleAnnotated) {
      problems.checkModifiers(
        field, isStatic, isPublic, ruleAnnotated, classRuleAnnotated, fieldName, javaPsi, sourcePsi, isOnTheFly, manager
      )
    }
    if (classRuleAnnotated) {
      problems.checkType(field, fieldName, sourcePsi, isOnTheFly, manager)
    }
    return problems.toTypedArray()
  }

  private fun MutableList<ProblemDescriptor>.checkModifiers(
    field: UField,
    isStatic: Boolean,
    isPublic: Boolean,
    ruleAnnotated: Boolean,
    classRuleAnnotated: Boolean,
    fieldName: PsiElement,
    javaPsi: PsiModifierListOwner,
    sourcePsi: PsiNameIdentifierOwner,
    isOnTheFly: Boolean,
    manager: InspectionManager
  ) {
    val message = getPublicStaticErrorMessage(isStatic, isPublic, ruleAnnotated, classRuleAnnotated) ?: return
    val actions = SmartList<IntentionAction>()
    if (!isPublic) {
      actions.addAll(createModifierActions(field, modifierRequest(JvmModifier.PUBLIC, true)))
    }
    if (ruleAnnotated) {
      if (isStatic) actions.addAll(createModifierActions(field, modifierRequest(JvmModifier.STATIC, false)))
    }
    else if (classRuleAnnotated) {
      if (!isStatic) actions.addAll(createModifierActions(field, modifierRequest(JvmModifier.STATIC, true)))
    }
    if (sourcePsi.language == Language.findLanguageByID("kotlin") &&
        javaPsi is JvmModifiersOwner && !javaPsi.hasAnnotation("kotlin.jvm.JvmField")
    ) {
      actions.addAll(createAddAnnotationActions(javaPsi, annotationRequest("kotlin.jvm.JvmField")))
    }
    val intention = IntentionWrapper.wrapToQuickFixes(actions, sourcePsi.containingFile).toTypedArray()
    val problem = manager.createProblemDescriptor(
      fieldName,
      JvmAnalysisBundle.message("jvm.inspections.junit.rule.problem.descriptor", if (ruleAnnotated) RULE_FQN else CLASS_RULE_FQN, message),
      isOnTheFly,
      intention,
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    )
    add(problem)
  }

  private fun MutableList<ProblemDescriptor>.checkType(
    field: UField,
    fieldName: PsiElement,
    sourcePsi: PsiNameIdentifierOwner,
    isOnTheFly: Boolean,
    manager: InspectionManager
  ) {
    val actions = SmartList<IntentionAction>()
    val aClass = PsiUtil.resolveClassInClassTypeOnly(field.type)
    if (InheritanceUtil.isInheritor(aClass, false, "org.junit.rules.TestRule") ||
        InheritanceUtil.isInheritor(aClass, false, "org.junit.rules.MethodRule")
    ) return
    val intention = IntentionWrapper.wrapToQuickFixes(actions, sourcePsi.containingFile).toTypedArray()
    val problem = manager.createProblemDescriptor(
      fieldName, JvmAnalysisBundle.message("jvm.inspections.junit.rule.type.problem.descriptor", CLASS_RULE_FQN), isOnTheFly,
      intention, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    )
    add(problem)
  }

  private fun getPublicStaticErrorMessage(
    isStatic: Boolean,
    isPublic: Boolean,
    shouldBeNonStatic: Boolean,
    shouldBeStatic: Boolean
  ): String? {
    if (!isPublic) {
      if (shouldBeStatic) {
        if (!isStatic) {
          return "'public' and 'static'"
        }
        else {
          return "'public'"
        }
      }
      else {
        if (!isStatic) {
          return "'public'"
        }
        else {
          return "'public' and non-static"
        }
      }
    }
    else {
      if (!isStatic) {
        if (shouldBeStatic) {
          return "'static'"
        }
      }
      else if (shouldBeNonStatic) {
        return "non-static"
      }
    }
    return null
  }

  companion object {
    const val RULE_FQN = "org.junit.Rule"
    const val CLASS_RULE_FQN = "org.junit.ClassRule"
  }
}