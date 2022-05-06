// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.*
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.*
import com.intellij.util.SmartList
import com.intellij.util.castSafelyTo
import org.jetbrains.uast.*
import java.util.*

class JUnitDataPointInspection : AbstractBaseUastLocalInspectionTool() {
  override fun checkMethod(method: UMethod, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> =
    checkDeclaration(method, manager, isOnTheFly, "Method")

  override fun checkField(field: UField, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> =
    checkDeclaration(field, manager, isOnTheFly, "Field")

  private fun checkDeclaration(
    declaration: UDeclaration,
    manager: InspectionManager,
    isOnTheFly: Boolean,
    memberDescription: @NlsSafe String
  ): Array<ProblemDescriptor> {
    val javaDecl = declaration.javaPsi.castSafelyTo<PsiMember>() ?: return emptyArray()
    val annotation = ANNOTATIONS.firstOrNull { AnnotationUtil.isAnnotated(javaDecl, it, 0) } ?: return emptyArray()
    val issues = getIssues(declaration)
    if (issues.isNotEmpty()) {
      val message = if (issues.size == 1) JvmAnalysisBundle.message(
        "jvm.inspections.junit.datapoint.problem.single.descriptor", memberDescription, annotation, issues.first()
      )
      else JvmAnalysisBundle.message( // size should always be 2
        "jvm.inspections.junit.datapoint.problem.double.descriptor", memberDescription, annotation, issues.first(), issues.last()
      )
      val place = declaration.uastAnchor?.sourcePsi ?: return emptyArray()
      val fixes = arrayOf(MakePublicStaticQuickfix(memberDescription, place.text, issues))
      val problemDescriptor = manager.createProblemDescriptor(
        place, message, isOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
      )
      return arrayOf(problemDescriptor)
    }
    return emptyArray()
  }

  private fun getIssues(declaration: UDeclaration): List<@NlsSafe String> = SmartList<String>().apply {
    if (declaration.visibility != UastVisibility.PUBLIC) add("public")
    if (!declaration.isStatic) add("static")
  }

  private class MakePublicStaticQuickfix(
    private val memberDescription: @NlsSafe String,
    private val memberName: @NlsSafe String,
    @FileModifier.SafeFieldForPreview private val issues: List<@NlsSafe String>
  ) : LocalQuickFix {
    override fun getName(): String = if (issues.size == 1) {
      JvmAnalysisBundle.message("jvm.inspections.junit.datapoint.fix.single.name",
                                memberDescription.lowercase(Locale.getDefault()), memberName, issues.first()
      )
    } else { // size should always be 2
      JvmAnalysisBundle.message("jvm.inspections.junit.datapoint.fix.double.name",
                                memberDescription.lowercase(Locale.getDefault()), memberName, issues.first(), issues.last()
      )
    }

    override fun getFamilyName(): String = JvmAnalysisBundle.message("jvm.inspections.junit.datapoint.fix.familyName")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val containingFile = descriptor.psiElement.containingFile ?: return
      val declaration = getUParentForIdentifier(descriptor.psiElement)?.castSafelyTo<UDeclaration>()?.javaPsi ?: return
      val declarationPtr = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(declaration)
      declarationPtr.invokeModifierRequest(JvmModifier.PUBLIC, containingFile)
      declarationPtr.invokeModifierRequest(JvmModifier.STATIC, containingFile)
    }

    private fun SmartPsiElementPointer<PsiElement>.invokeModifierRequest(modifier: JvmModifier, containingFile: PsiFile) {
      element?.castSafelyTo<JvmModifiersOwner>()?.let { elem ->
        createModifierActions(elem, modifierRequest(modifier, true)).forEach {
          it.invoke(project, null, containingFile)
        }
      } ?: return
    }
  }

  companion object {
    private const val DATAPOINT = "org.junit.experimental.theories.DataPoint"
    private const val DATAPOINTS = "org.junit.experimental.theories.DataPoints"

    private val ANNOTATIONS = arrayOf(DATAPOINT, DATAPOINTS)
  }
}