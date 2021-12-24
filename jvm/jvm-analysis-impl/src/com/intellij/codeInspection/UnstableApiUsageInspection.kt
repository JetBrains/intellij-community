// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.StaticAnalysisAnnotationManager
import com.intellij.codeInspection.AnnotatedApiUsageUtil.findAnnotatedContainingDeclaration
import com.intellij.codeInspection.AnnotatedApiUsageUtil.findAnnotatedTypeUsedInDeclarationSignature
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor
import com.intellij.codeInspection.deprecation.DeprecationInspection
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.SpecialAnnotationsUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.ArrayUtilRt
import com.siyeh.ig.ui.ExternalizableStringSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import javax.swing.JPanel

class UnstableApiUsageInspection : LocalInspectionTool() {

  companion object {

    private val SCHEDULED_FOR_REMOVAL_ANNOTATION_NAME: String = ApiStatus.ScheduledForRemoval::class.java.canonicalName

    private val knownAnnotationMessageProviders = mapOf(SCHEDULED_FOR_REMOVAL_ANNOTATION_NAME to ScheduledForRemovalMessageProvider())
  }

  @JvmField
  val unstableApiAnnotations: List<String> =
    ExternalizableStringSet(*ArrayUtilRt.toStringArray(StaticAnalysisAnnotationManager.getInstance().knownUnstableApiAnnotations))

  @JvmField
  var myIgnoreInsideImports: Boolean = true

  @JvmField
  var myIgnoreApiDeclaredInThisProject: Boolean = true

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val annotations = unstableApiAnnotations.toList()
    return if (annotations.any { AnnotatedApiUsageUtil.canAnnotationBeUsedInFile(it, holder.file) }) {
      ApiUsageUastVisitor.createPsiElementVisitor(
        UnstableApiUsageProcessor(
          holder,
          myIgnoreInsideImports,
          myIgnoreApiDeclaredInThisProject,
          annotations,
          knownAnnotationMessageProviders
        )
      )
    } else {
      PsiElementVisitor.EMPTY_VISITOR
    }
  }

  override fun createOptionsPanel(): JPanel {
    val panel = MultipleCheckboxOptionsPanel(this)
    panel.addCheckbox(JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.ignore.inside.imports"), "myIgnoreInsideImports")
    panel.addCheckbox(JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.ignore.declared.inside.this.project"), "myIgnoreApiDeclaredInThisProject")

    //TODO in add annotation window "Include non-project items" should be enabled by default
    val annotationsListControl = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      unstableApiAnnotations, JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.annotations.list")
    )

    panel.add(annotationsListControl, "growx, wrap")
    return panel
  }
}

private class UnstableApiUsageProcessor(
  private val problemsHolder: ProblemsHolder,
  private val ignoreInsideImports: Boolean,
  private val ignoreApiDeclaredInThisProject: Boolean,
  private val unstableApiAnnotations: List<String>,
  private val knownAnnotationMessageProviders: Map<String, UnstableApiUsageMessageProvider>
) : ApiUsageProcessor {

  private companion object {
    fun isLibraryElement(element: PsiElement): Boolean {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        return true
      }
      val containingVirtualFile = PsiUtilCore.getVirtualFile(element)
      return containingVirtualFile != null && ProjectFileIndex.getInstance(element.project).isInLibraryClasses(containingVirtualFile)
    }
  }

  override fun processImportReference(sourceNode: UElement, target: PsiModifierListOwner) {
    if (!ignoreInsideImports) {
      checkUnstableApiUsage(target, sourceNode, false)
    }
  }

  override fun processReference(sourceNode: UElement, target: PsiModifierListOwner, qualifier: UExpression?) {
    checkUnstableApiUsage(target, sourceNode, false)
  }

  override fun processConstructorInvocation(
    sourceNode: UElement,
    instantiatedClass: PsiClass,
    constructor: PsiMethod?,
    subclassDeclaration: UClass?
  ) {
    if (constructor != null) {
      checkUnstableApiUsage(constructor, sourceNode, false)
    }
  }

  override fun processMethodOverriding(method: UMethod, overriddenMethod: PsiMethod) {
    checkUnstableApiUsage(overriddenMethod, method, true)
  }

  private fun getMessageProvider(psiAnnotation: PsiAnnotation): UnstableApiUsageMessageProvider? {
    val annotationName = psiAnnotation.qualifiedName ?: return null
    return knownAnnotationMessageProviders[annotationName] ?: DefaultUnstableApiUsageMessageProvider
  }

  private fun getElementToHighlight(sourceNode: UElement): PsiElement? =
    (sourceNode as? UDeclaration)?.uastAnchor.sourcePsiElement ?: sourceNode.sourcePsi

  private fun checkUnstableApiUsage(target: PsiModifierListOwner, sourceNode: UElement, isMethodOverriding: Boolean) {
    if (ignoreApiDeclaredInThisProject && !isLibraryElement(target)) {
      return
    }

    if (checkTargetIsUnstableItself(target, sourceNode, isMethodOverriding)) {
      return
    }

    checkTargetReferencesUnstableTypeInSignature(target, sourceNode, isMethodOverriding)
  }

  private fun checkTargetIsUnstableItself(target: PsiModifierListOwner, sourceNode: UElement, isMethodOverriding: Boolean): Boolean {
    val annotatedContainingDeclaration = findAnnotatedContainingDeclaration(target, unstableApiAnnotations, true)
    if (annotatedContainingDeclaration != null) {
      val messageProvider = getMessageProvider(annotatedContainingDeclaration.psiAnnotation) ?: return false
      val message = if (isMethodOverriding) {
        messageProvider.buildMessageUnstableMethodOverridden(annotatedContainingDeclaration)
      }
      else {
        messageProvider.buildMessage(annotatedContainingDeclaration)
      }
      val elementToHighlight = getElementToHighlight(sourceNode) ?: return false
      problemsHolder.registerProblem(elementToHighlight, message, messageProvider.problemHighlightType)
      return true
    }
    return false
  }

  private fun checkTargetReferencesUnstableTypeInSignature(target: PsiModifierListOwner, sourceNode: UElement, isMethodOverriding: Boolean) {
    if (!isMethodOverriding && !arePsiElementsFromTheSameFile(sourceNode.sourcePsi, target.containingFile)) {
      val declaration = target.toUElement(UDeclaration::class.java)
      if (declaration !is UClass && declaration !is UMethod && declaration !is UField) {
        return
      }
      val unstableTypeUsedInSignature = findAnnotatedTypeUsedInDeclarationSignature(declaration, unstableApiAnnotations)
      if (unstableTypeUsedInSignature != null) {
        val messageProvider = getMessageProvider(unstableTypeUsedInSignature.psiAnnotation) ?: return
        val message = messageProvider.buildMessageUnstableTypeIsUsedInSignatureOfReferencedApi(target, unstableTypeUsedInSignature)
        val elementToHighlight = getElementToHighlight(sourceNode) ?: return
        problemsHolder.registerProblem(elementToHighlight, message, messageProvider.problemHighlightType)
      }
    }
  }

  private fun arePsiElementsFromTheSameFile(one: PsiElement?, two: PsiElement?): Boolean {
    //For Kotlin: naive comparison of PSI containingFile-s does not work because one of the PSI elements might be light PSI element
    // coming from a light PSI file, and another element would be physical PSI file, and they are not "equals()".
    return one?.containingFile?.virtualFile == two?.containingFile?.virtualFile
  }

}

private interface UnstableApiUsageMessageProvider {

  val problemHighlightType: ProblemHighlightType

  @InspectionMessage
  fun buildMessage(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String

  @InspectionMessage
  fun buildMessageUnstableMethodOverridden(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String

  @InspectionMessage
  fun buildMessageUnstableTypeIsUsedInSignatureOfReferencedApi(
    referencedApi: PsiModifierListOwner,
    annotatedTypeUsedInSignature: AnnotatedContainingDeclaration
  ): String
}

private object DefaultUnstableApiUsageMessageProvider : UnstableApiUsageMessageProvider {

  override val problemHighlightType
    get() = ProblemHighlightType.GENERIC_ERROR_OR_WARNING

  override fun buildMessageUnstableMethodOverridden(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String =
    with(annotatedContainingDeclaration) {
      if (isOwnAnnotation) {
        JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.overridden.method.is.marked.unstable.itself", targetName, presentableAnnotationName)
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