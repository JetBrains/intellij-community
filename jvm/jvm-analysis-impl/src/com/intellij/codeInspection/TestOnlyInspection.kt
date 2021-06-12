// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.TestFrameworks
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.impl.source.resolve.JavaResolveUtil
import com.intellij.uast.UastVisitorAdapter
import org.jetbrains.uast.*
import kotlin.math.min

class TestOnlyInspection : AbstractBaseUastLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    UastVisitorAdapter(TestOnlyApiUsageVisitor(TestOnlyApiUsageProcessor(holder), holder), true)

  inner class TestOnlyApiUsageVisitor(
    processor: TestOnlyApiUsageProcessor,
    private val holder: ProblemsHolder
  ) : ApiUsageUastVisitor(processor) {
    override fun visitClass(node: UClass): Boolean {
      super.visitClass(node)
      checkDoubleAnnotation(node)
      return true
    }

    override fun visitMethod(node: UMethod): Boolean {
      super.visitMethod(node)
      checkDoubleAnnotation(node)
      return true
    }

    override fun visitField(node: UField): Boolean {
      super.visitField(node)
      checkDoubleAnnotation(node)
      return true
    }

    private fun checkDoubleAnnotation(elem: UDeclaration) {
      val vft = elem.uAnnotations.find { visibleForTestingAnnotations.contains(it.qualifiedName) } ?: return
      if (isDirectlyTestOnly(elem)) {
        val toHighlight = vft.uastAnchor.sourcePsiElement ?: return
        val vftJavaPsi = vft.javaPsi ?: return
        val elemJavaPsi = elem.javaPsi
        holder.registerProblem(
          toHighlight,
          JvmAnalysisBundle.message("jvm.inspections.testonly.visiblefortesting"),
          RemoveAnnotationQuickFix(vftJavaPsi, elemJavaPsi as PsiModifierListOwner)
        )
      }
      return
    }
  }

  inner class TestOnlyApiUsageProcessor(private val holder: ProblemsHolder) : ApiUsageProcessor {
    override fun processReference(sourceNode: UElement, target: PsiModifierListOwner, qualifier: UExpression?) {
      if (target is PsiMember) validate(sourceNode, target, holder)
    }

    override fun processConstructorInvocation(
      sourceNode: UElement,
      instantiatedClass: PsiClass,
      constructor: PsiMethod?,
      subclassDeclaration: UClass?
    ) {
      constructor?.let { validate(sourceNode, it, holder) }
    }

    private fun validate(place: UElement, member: PsiModifierListOwner, holder: ProblemsHolder) {
      val sourcePsi = place.sourcePsi ?: return
      val uMember = member.toUElement() ?: return
      if (uMember !is UDeclaration) return
      val vft = AnnotationUtil.findAnnotation(member, visibleForTestingAnnotations)
      if (vft == null && !isAnnotatedAsTestOnly(uMember)) return
      if (isInsideTestOnlyMethod(place)) return
      if (isInsideTestOnlyField(place)) return
      if (isInsideTestOnlyClass(place)) return
      if (isInsideTestClass(place)) return
      if (isUnderTestSources(sourcePsi)) return
      if (vft != null && member is PsiMember) {
        var modifier = getAccessModifierWithoutTesting(vft)
        if (modifier == null) modifier = getNextLowerAccessLevel(member)
        val modList = LightModifierList(member.manager, JavaLanguage.INSTANCE, modifier)
        if (JavaResolveUtil.isAccessible(member, member.containingClass, modList, sourcePsi, null, null)) return
      }
      reportProblem(sourcePsi, member, holder)
    }

    private fun getNextLowerAccessLevel(member: PsiModifierListOwner): String {
      val methodModifier = modifierPriority.indexOfFirst { name -> member.hasModifierProperty(name) }
      var minModifier = modifierPriority.size - 1
      if (member is PsiMethod) {
        for (superMethod in member.findSuperMethods()) {
          minModifier = min(minModifier, modifierPriority.indexOfFirst { name -> superMethod.hasModifierProperty(name) })
        }
      }
      return modifierPriority[min(minModifier, methodModifier + 1)]
    }

    private fun getAccessModifierWithoutTesting(anno: PsiAnnotation): String? {
      val ref = anno.findAttributeValue("visibility")
      if (ref is PsiReferenceExpression) {
        val target = ref.resolve()
        if (target is PsiEnumConstant) {
          return when (target.name) {
            "PRIVATE" -> PsiModifier.PRIVATE
            "PROTECTED" -> PsiModifier.PROTECTED
            else -> PsiModifier.PACKAGE_LOCAL
          }
        }
      }
      return null
    }

    private fun isInsideTestOnlyMethod(elem: UElement) = isAnnotatedAsTestOnly(elem.getTopLevelParentOfType(UMethod::class.java))

    private fun isInsideTestOnlyField(elem: UElement) = isAnnotatedAsTestOnly(elem.getTopLevelParentOfType(UField::class.java))

    private fun isInsideTestOnlyClass(elem: UElement) = isAnnotatedAsTestOnly(elem.getTopLevelParentOfType(UClass::class.java))

    private fun isAnnotatedAsTestOnly(member: UDeclaration?): Boolean {
      return member != null && (isDirectlyTestOnly(member) || isAnnotatedAsTestOnly(member.getContainingUClass()))
    }

    private fun isInsideTestClass(elem: UElement): Boolean {
      val parent = elem.getTopLevelParentOfType(UClass::class.java)
      val javaPsi = parent?.javaPsi ?: return false
      return TestFrameworks.getInstance().isTestClass(javaPsi)
    }

    private fun <T : UElement> UElement.getTopLevelParentOfType(c: Class<out T>): T? {
      var parent = getParentOfType(c) ?: return null
      do {
        parent = parent.getParentOfType(c) ?: return parent
      }
      while (true)
    }

    private fun isUnderTestSources(elem: PsiElement): Boolean {
      val rootManger = ProjectRootManager.getInstance(elem.project)
      val file = elem.containingFile.virtualFile
      return file != null && rootManger.fileIndex.isInTestSourceContent(file)
    }

    private fun reportProblem(elem: PsiElement, target: PsiModifierListOwner, holder: ProblemsHolder) {
      val message = JvmAnalysisBundle.message(
        when {
          target is PsiMethod && target.isConstructor -> "jvm.inspections.testonly.class.reference"
          target is PsiField -> "jvm.inspections.testonly.field.reference"
          else -> "jvm.inspections.testonly.method.call"
        }
      )
      holder.registerProblem(elem, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    }
  }

  private fun isDirectlyTestOnly(member: UDeclaration) = member.findAnnotation(AnnotationUtil.TEST_ONLY) != null

  companion object {
    private val visibleForTestingAnnotations = listOf(
      "com.google.common.annotations.VisibleForTesting",
      "com.android.annotations.VisibleForTesting",
      "org.jetbrains.annotations.VisibleForTesting"
    )

    private val modifierPriority = listOf(PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE)
  }
}