// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.options.OptPane.string
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.sourcePsiElement
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Reports declarations (classes, methods, fields) marked with [ApiStatus.ScheduledForRemoval] annotation
 * that must already be removed. [ApiStatus.ScheduledForRemoval.inVersion] value is compared with "current" version.
 */
class MustAlreadyBeRemovedApiInspection : AbstractBaseUastLocalInspectionTool() {
  var currentVersion: String = ""

  override fun getOptionsPane(): OptPane = pane(string("currentVersion", JvmAnalysisBundle.message("current.version")))

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (currentVersion.isEmpty() || !AnnotatedApiUsageUtil.canAnnotationBeUsedInFile(SCHEDULED_FOR_REMOVAL_ANNOTATION_NAME, holder.file)) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      MustAlreadyBeRemovedApiVisitor(holder, currentVersion),
      arrayOf(UDeclaration::class.java),
      true
    )
  }

  private class MustAlreadyBeRemovedApiVisitor(
    private val problemsHolder: ProblemsHolder,
    private val currentVersion: String
  ) : AbstractUastNonRecursiveVisitor() {
    override fun visitDeclaration(node: UDeclaration): Boolean {
      val versionOfScheduledRemoval = getVersionOfScheduledRemoval(node)
      if (versionOfScheduledRemoval != null && VersionComparatorUtil.compare(currentVersion, versionOfScheduledRemoval) >= 0) {
        val message = if (currentVersion == versionOfScheduledRemoval) {
          JvmAnalysisBundle.message(
            "jvm.inspections.must.already.be.removed.api.current.version.description",
            currentVersion
          )
        }
        else {
          JvmAnalysisBundle.message(
            "jvm.inspections.must.already.be.removed.api.earlier.version.description",
            versionOfScheduledRemoval,
            currentVersion
          )
        }

        val identifierPsi = node.uastAnchor.sourcePsiElement ?: return true
        problemsHolder.registerProblem(identifierPsi, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
      return true
    }

    private fun getVersionOfScheduledRemoval(annotated: UAnnotated): String? {
      val annotation = annotated.findAnnotation(SCHEDULED_FOR_REMOVAL_ANNOTATION_NAME) ?: return null
      return annotation.findDeclaredAttributeValue("inVersion")?.evaluateString()
    }
  }

  private companion object {
    private val SCHEDULED_FOR_REMOVAL_ANNOTATION_NAME = ApiStatus.ScheduledForRemoval::class.java.canonicalName
  }
}

