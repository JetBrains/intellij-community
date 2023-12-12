// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.options.OptPane
import com.intellij.lang.LanguageCommenters
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.lang.jvm.actions.annotationRequest
import com.intellij.lang.jvm.actions.createRemoveAnnotationActions
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

private class SuppressAnnotationDescriptor(val pkg: String, val shortName: String, val attributeValue: String)

private val suppressAnnotations = listOf(
  SuppressAnnotationDescriptor("kotlin", "Suppress", "names"),
  SuppressAnnotationDescriptor("java.lang", "SuppressWarnings", "value")
)

@VisibleForTesting
class SuppressionAnnotationInspection : AbstractBaseUastLocalInspectionTool() {
  var myAllowedSuppressions: MutableList<String> = ArrayList()

  override fun getOptionsPane(): OptPane {
    return OptPane.pane(OptPane.stringList("myAllowedSuppressions", JvmAnalysisBundle.message("ignored.suppressions")))
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      SuppressionAnnotationVisitor(holder),
      arrayOf(UAnnotation::class.java, UComment::class.java),
      true
    )
  }

  override fun isSuppressedFor(element: PsiElement): Boolean {
    return false
  }

  override fun getBatchSuppressActions(element: PsiElement?): Array<SuppressQuickFix> {
    return SuppressQuickFix.EMPTY_ARRAY
  }

  private inner class SuppressionAnnotationVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
    override fun visitComment(node: UComment): Boolean {
      if (isSuppressComment(node)) {
        val ids = getIdsFromComment(node.text)
        if (ids != null) {
          for (id in ids) {
            if (!myAllowedSuppressions.contains(id)) {
              registerProblem(node, true)
              break
            }
          }
        }
        else {
          registerProblem(node, false)
        }
      }
      return true
    }

    private fun isSuppressComment(comment: UComment): Boolean {
      val text = comment.text
      val commentPrefix = LanguageCommenters.INSTANCE.forLanguage(comment.lang)?.lineCommentPrefix ?: return false
      if (text.length <= commentPrefix.length && !text.startsWith(commentPrefix)) {
        return false
      }
      val strippedComment = text.substring(commentPrefix.length).trim()
      return strippedComment.startsWith(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME)
    }

    private fun registerProblem(comment: UComment, addAllowSuppressionsFix: Boolean) {
      val fixes = if (addAllowSuppressionsFix) arrayOf(RemoveSuppressCommentFix(), AllowSuppressionsFix())
      else arrayOf(RemoveSuppressCommentFix())
      holder.registerProblem(comment.sourcePsi, JvmAnalysisBundle.message("inspection.suppression.annotation.problem.descriptor"), *fixes)
    }

    override fun visitAnnotation(node: UAnnotation): Boolean {
      val suppressDescriptor = node.suppressDescriptor()
      if (suppressDescriptor == null) return true
      val suppressIds = node.suppressIds(suppressDescriptor.attributeValue)
      when {
        suppressIds.isNotEmpty() && !myAllowedSuppressions.containsAll(suppressIds) -> registerProblem(node, true)
        suppressIds.isEmpty() -> registerProblem(node, false)
      }
      return true
    }

    private fun registerProblem(annotation: UAnnotation, addAllowSuppressionsFix: Boolean) {
      val sourcePsi = annotation.sourcePsi ?: return
      var fixes: Array<LocalQuickFix> = if (addAllowSuppressionsFix) arrayOf(AllowSuppressionsFix()) else arrayOf()
      val removeAnnotationFix = getRemoveAnnotationFix(annotation, sourcePsi)
      if (removeAnnotationFix != null) {
        fixes += removeAnnotationFix
      }
      holder.registerProblem(sourcePsi, JvmAnalysisBundle.message("inspection.suppression.annotation.problem.descriptor"), *fixes)
    }

    private fun getRemoveAnnotationFix(annotationElement: UAnnotation, sourcePsi: PsiElement): LocalQuickFix? {
      val owner = annotationElement.getParentOfType<UDeclaration>()?.javaPsi as? JvmModifiersOwner ?: return null
      val annotationQualifiedName = annotationElement.qualifiedName ?: return null
      val actions = createRemoveAnnotationActions(owner, annotationRequest(annotationQualifiedName))
      return IntentionWrapper.wrapToQuickFixes(actions, sourcePsi.containingFile).singleOrNull()
    }
  }

  private class RemoveSuppressCommentFix : PsiUpdateModCommandQuickFix() {
    override fun applyFix(project: Project, startElement: PsiElement, updater: ModPsiUpdater) {
      startElement.delete()
    }

    override fun getFamilyName(): String {
      return JvmAnalysisBundle.message("remove.suppress.comment.fix.family.name", SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME)
    }
  }

  private inner class AllowSuppressionsFix : ModCommandQuickFix() {
    override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
      val psiElement = descriptor.psiElement
      val ids = getIds(psiElement) ?: return ModCommand.nop()
      return ModCommand.updateInspectionOption(psiElement, this@SuppressionAnnotationInspection) { inspection ->
        for (id in ids) {
          if (!inspection.myAllowedSuppressions.contains(id)) {
            inspection.myAllowedSuppressions.add(id)
          }
        }
      }
    }

    private fun getIds(psiElement: PsiElement): Collection<String>? {
      val annotation = psiElement.toUElement()?.getParentOfType<UAnnotation>(strict = false)
      if (annotation != null) {
        val attributeValue = annotation.suppressDescriptor()?.attributeValue ?: return null
        return annotation.suppressIds(attributeValue)
      }
      else {
        val comment = psiElement.toUElement(UComment::class.java) ?: return null
        return getIdsFromComment(comment.text)
      }
    }

    override fun getName(): String {
      return JvmAnalysisBundle.message("allow.suppressions.fix.text")
    }

    override fun getFamilyName(): String {
      return JvmAnalysisBundle.message("allow.suppressions.fix.family.name")
    }
  }
}

private fun UAnnotation.suppressDescriptor(): SuppressAnnotationDescriptor? {
  val shortName = uastAnchor?.name ?: return null
  if (suppressAnnotations.none { descr -> descr.shortName == shortName }) return null
  return suppressAnnotations.firstOrNull { descr -> "${descr.pkg}.${descr.shortName}" == qualifiedName }
}

private fun UAnnotation.suppressIds(attributeName: String): List<String> = flattenedAttributeValues(attributeName).mapNotNull {
  when (it) {
    is UInjectionHost, is UReferenceExpression -> it.evaluateString()
    else -> null
  }
}

private fun getIdsFromComment(commentText: String): List<String>? {
  val matcher = SuppressionUtil.SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(commentText)
  if (matcher.matches()) {
    val suppressedIds = matcher.group(1).trim()
    return StringUtil.tokenize(suppressedIds, ",").toList().map { it.trim() }
  }
  else {
    return null
  }
}