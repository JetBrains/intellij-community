// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.StaticAnalysisAnnotationManager
import com.intellij.codeInsight.options.JavaClassValidator
import com.intellij.codeInspection.deprecation.DeprecationInspection
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.options.OptPane.stringList
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.siyeh.ig.ui.ExternalizableStringSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
class UnstableApiUsageInspection : UnstableApiUsageInspectionBase() {

  private inline val SCHEDULED_FOR_REMOVAL_ANNOTATION_NAME: String get() = ApiStatus.ScheduledForRemoval::class.java.canonicalName

  private val knownAnnotationMessageProviders = mapOf(SCHEDULED_FOR_REMOVAL_ANNOTATION_NAME to ScheduledForRemovalMessageProvider())

  @JvmField
  val unstableApiAnnotations: List<String> =
    ExternalizableStringSet(*StaticAnalysisAnnotationManager.getInstance().knownUnstableApiAnnotations)

  @JvmField
  var myIgnoreInsideImports: Boolean = true

  @JvmField
  var myIgnoreApiDeclaredInThisProject: Boolean = true

  override val annotationsToCheck: List<String>
    get() = unstableApiAnnotations.toList()

  override val ignoreInsideImports: Boolean
    get() = myIgnoreInsideImports

  override val ignoreApiDeclaredInThisProject: Boolean
    get() = myIgnoreApiDeclaredInThisProject

  override fun getMessageProvider(annotationFqn: String): UnstableApiUsageMessageProvider =
    knownAnnotationMessageProviders[annotationFqn] ?: DefaultUnstableApiUsageMessageProvider

  override fun isUsageIgnored(usage: PsiElement, annotationFqn: String): Boolean =
    UnstableApiUsageFilter.EP_NAME.extensionList.any { it.isUsageIgnored(usage, annotationFqn) }

  override fun getOptionsPane(): OptPane {
    return pane(
      checkbox("myIgnoreInsideImports", JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.ignore.inside.imports")),
      checkbox("myIgnoreApiDeclaredInThisProject",
               JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.ignore.declared.inside.this.project")),
      //TODO in add annotation window "Include non-project items" should be enabled by default
      stringList("unstableApiAnnotations", JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.annotations.list"),
                 JavaClassValidator().annotationsOnly())
    )
  }
}

private object DefaultUnstableApiUsageMessageProvider : UnstableApiUsageMessageProvider {

  override val problemHighlightType
    get() = ProblemHighlightType.GENERIC_ERROR_OR_WARNING

  override fun buildMessageUnstableMethodOverridden(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String =
    with(annotatedContainingDeclaration) {
      if (isOwnAnnotation) {
        JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.overridden.method.is.marked.unstable.itself", targetName,
                                  presentableAnnotationName)
      }
      else {
        JvmAnalysisBundle.message(
          "jvm.inspections.unstable.api.usage.overridden.method.is.declared.in.unstable.api",
          targetName,
          containingDeclarationType,
          containingDeclarationName,
          presentableAnnotationName
        )
      }
    }

  override fun buildMessage(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String =
    with(annotatedContainingDeclaration) {
      if (isOwnAnnotation) {
        JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.api.is.marked.unstable.itself", targetName, presentableAnnotationName)
      }
      else {
        JvmAnalysisBundle.message(
          "jvm.inspections.unstable.api.usage.api.is.declared.in.unstable.api",
          targetName,
          containingDeclarationType,
          containingDeclarationName,
          presentableAnnotationName
        )
      }
    }

  override fun buildMessageUnstableTypeIsUsedInSignatureOfReferencedApi(
    referencedApi: PsiModifierListOwner,
    annotatedTypeUsedInSignature: AnnotatedContainingDeclaration
  ): String = JvmAnalysisBundle.message(
    "jvm.inspections.unstable.api.usage.unstable.type.is.used.in.signature.of.referenced.api",
    DeprecationInspection.getPresentableName(referencedApi),
    annotatedTypeUsedInSignature.targetType,
    annotatedTypeUsedInSignature.targetName,
    annotatedTypeUsedInSignature.presentableAnnotationName
  )
}

private class ScheduledForRemovalMessageProvider : UnstableApiUsageMessageProvider {

  override val problemHighlightType
    get() = ProblemHighlightType.GENERIC_ERROR

  override fun buildMessageUnstableMethodOverridden(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String {
    val versionMessage = getVersionMessage(annotatedContainingDeclaration)
    return with(annotatedContainingDeclaration) {
      if (isOwnAnnotation) {
        JvmAnalysisBundle.message(
          "jvm.inspections.scheduled.for.removal.method.overridden.marked.itself",
          targetName,
          versionMessage
        )
      }
      else {
        JvmAnalysisBundle.message(
          "jvm.inspections.scheduled.for.removal.method.overridden.declared.in.marked.api",
          targetName,
          containingDeclarationType,
          containingDeclarationName,
          versionMessage
        )
      }
    }
  }

  override fun buildMessage(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String {
    val versionMessage = getVersionMessage(annotatedContainingDeclaration)
    return with(annotatedContainingDeclaration) {
      if (!isOwnAnnotation) {
        JvmAnalysisBundle.message(
          "jvm.inspections.scheduled.for.removal.api.is.declared.in.marked.api",
          targetName,
          containingDeclarationType,
          containingDeclarationName,
          versionMessage
        )
      }
      else {
        JvmAnalysisBundle.message(
          "jvm.inspections.scheduled.for.removal.api.is.marked.itself", targetName, versionMessage
        )
      }
    }
  }

  override fun buildMessageUnstableTypeIsUsedInSignatureOfReferencedApi(
    referencedApi: PsiModifierListOwner,
    annotatedTypeUsedInSignature: AnnotatedContainingDeclaration
  ): String {
    val versionMessage = getVersionMessage(annotatedTypeUsedInSignature)
    return JvmAnalysisBundle.message(
      "jvm.inspections.scheduled.for.removal.scheduled.for.removal.type.is.used.in.signature.of.referenced.api",
      DeprecationInspection.getPresentableName(referencedApi),
      annotatedTypeUsedInSignature.targetType,
      annotatedTypeUsedInSignature.targetName,
      versionMessage
    )
  }

  private fun getVersionMessage(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String {
    val versionValue = AnnotationUtil.getDeclaredStringAttributeValue(annotatedContainingDeclaration.psiAnnotation, "inVersion")
    return if (versionValue.isNullOrEmpty()) {
      JvmAnalysisBundle.message("jvm.inspections.scheduled.for.removal.future.version")
    }
    else {
      JvmAnalysisBundle.message("jvm.inspections.scheduled.for.removal.predefined.version", versionValue)
    }
  }
}
