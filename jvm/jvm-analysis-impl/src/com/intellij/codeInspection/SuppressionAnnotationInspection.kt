// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.options.OptPane
import com.intellij.ide.nls.NlsMessages
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

@VisibleForTesting
class SuppressionAnnotationInspection : AbstractBaseUastLocalInspectionTool() {
  var myAllowedSuppressions: MutableList<String> = mutableListOf()

  override fun getOptionsPane(): OptPane = OptPane.pane(
    OptPane.stringList("myAllowedSuppressions", JvmAnalysisBundle.message("ignored.suppressions"))
  )

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      SuppressionAnnotationVisitor(holder, myAllowedSuppressions, this),
      arrayOf(UAnnotation::class.java, UComment::class.java),
      true
    )
  }

  override fun isSuppressedFor(element: PsiElement): Boolean = false

  override fun getBatchSuppressActions(element: PsiElement?): Array<SuppressQuickFix> = SuppressQuickFix.EMPTY_ARRAY
}

private class SuppressAnnotationDescriptor(val pkg: String, val shortName: String, val attributeValue: String)

private val suppressAnnotations = listOf(
  SuppressAnnotationDescriptor("kotlin", "Suppress", "names"),
  SuppressAnnotationDescriptor("java.lang", "SuppressWarnings", "value")
)

private fun UAnnotation.suppressIds(): List<String>? {
  fun UAnnotation.suppressDescriptor(): SuppressAnnotationDescriptor? {
    val shortName = uastAnchor?.name ?: return null
    if (suppressAnnotations.none { descr -> descr.shortName == shortName }) return null
    return suppressAnnotations.firstOrNull { descr -> "${descr.pkg}.${descr.shortName}" == qualifiedName }
  }

  val suppressDescriptor = suppressDescriptor()
  if (suppressDescriptor == null) return null
  return flattenedAttributeValues(suppressDescriptor.attributeValue).mapNotNull {
    when (it) {
      is UInjectionHost, is UReferenceExpression -> it.evaluateString()
      else -> null
    }
  }
}

private fun UComment.suppressIds(): List<String>? {
  fun UComment.isSuppressComment(): Boolean {
    val commentPrefix = LanguageCommenters.INSTANCE.forLanguage(lang)?.lineCommentPrefix ?: return false
    if (text.length <= commentPrefix.length && !text.startsWith(commentPrefix)) return false
    val strippedComment = text.substring(commentPrefix.length).trim()
    return strippedComment.startsWith(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME)
  }
  if (!isSuppressComment()) return null

  val matcher = SuppressionUtil.SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(text)
  if (!matcher.matches()) return emptyList()
  val suppressedIds = matcher.group(1).trim()
  return StringUtil.tokenize(suppressedIds, ",").toList().map { it.trim() }
}

private class SuppressionAnnotationVisitor(
  private val holder: ProblemsHolder,
  private val allowedSuppressions: List<String>,
  private val inspection: SuppressionAnnotationInspection // quick fixes might change inspection settings
) : AbstractUastNonRecursiveVisitor() {
  override fun visitComment(node: UComment): Boolean {
    val suppressIds = node.suppressIds() ?: return true
    val fixes = when {
      suppressIds.isEmpty() -> arrayOf(RemoveSuppressCommentFix())
      suppressIds.any { !allowedSuppressions.contains(it) } -> arrayOf(RemoveSuppressCommentFix(), AllowSuppressionsFix(inspection))
      else -> return true
    }
    val message = JvmAnalysisBundle.message(
      "inspection.suppression.comment.problem.descriptor",
      NlsMessages.formatAndList(suppressIds.map { "'$it'" })
    )
    holder.registerProblem(node.sourcePsi, message, *fixes)
    return true
  }

  override fun visitAnnotation(node: UAnnotation): Boolean {
    val suppressIds = node.suppressIds() ?: return true
    val fixes = when {
      suppressIds.isEmpty() -> node.removeAnnotationFixes()
      suppressIds.any { !allowedSuppressions.contains(it) } -> listOf(AllowSuppressionsFix(inspection)) + node.removeAnnotationFixes()
      else -> return true
    }
    val message = JvmAnalysisBundle.message(
      "inspection.suppression.annotation.problem.descriptor",
      NlsMessages.formatAndList(suppressIds.map { "'$it'" })
    )
    holder.registerUProblem(node, message, *fixes.toTypedArray())
    return true
  }

  private fun UAnnotation.removeAnnotationFixes(): List<LocalQuickFix> {
    val owner = getParentOfType<UDeclaration>()?.javaPsi as? JvmModifiersOwner ?: return emptyList()
    val file = sourcePsi?.containingFile ?: return emptyList()
    val fqn = qualifiedName ?: return emptyList()
    return IntentionWrapper.wrapToQuickFixes(createRemoveAnnotationActions(owner, annotationRequest(fqn)), file)
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

private class AllowSuppressionsFix(private val inspection: SuppressionAnnotationInspection) : ModCommandQuickFix() {
  override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
    val anchor = descriptor.psiElement
    val suppressIds = anchor.suppressIdsFromAnchor() ?: return ModCommand.nop()
    if (suppressIds.isEmpty()) return ModCommand.nop()
    return ModCommand.updateInspectionOption(anchor, inspection) { inspection ->
      for (suppressId in suppressIds) {
        if (!inspection.myAllowedSuppressions.contains(suppressId)) {
          inspection.myAllowedSuppressions.add(suppressId)
        }
      }
    }
  }

  private fun PsiElement.suppressIdsFromAnchor(): List<String>? {
    return when (val suppressIdContainer = getUastParentOfTypes(arrayOf(UComment::class.java, UAnnotation::class.java), strict = false)) {
      is UComment -> suppressIdContainer.suppressIds()
      is UAnnotation -> suppressIdContainer.suppressIds()
      else -> emptyList()
    }
  }

  override fun getName(): String {
    return JvmAnalysisBundle.message("allow.suppressions.fix.text")
  }

  override fun getFamilyName(): String {
    return JvmAnalysisBundle.message("allow.suppressions.fix.family.name")
  }
}