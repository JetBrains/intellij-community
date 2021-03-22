// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.TestFrameworks
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.impl.source.resolve.JavaResolveUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.uast.UastVisitorAdapter
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import kotlin.math.min

class TestOnlyInspection : AbstractBaseUastLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    UastVisitorAdapter(TestOnlyVisitor(holder), true)

  class TestOnlyVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
    override fun visitCallExpression(node: UCallExpression): Boolean {
      val parent = node.uastParent
      if (parent is UQualifiedReferenceExpression) {
        val parentRecResolved = parent.receiver.tryResolve()
        if (parentRecResolved is PsiPackage || parentRecResolved is PsiVariable || parentRecResolved is PsiClass) return true
      }
      val method = node.resolve() ?: return true
      val sourcePsi = node.sourcePsi ?: return true
      return validate(sourcePsi, method, holder)
    }

    override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
      if (node.uastParent is UQualifiedReferenceExpression) return true
      val resolve = node.resolveToUElement()
      val resolvedJava = resolve?.javaPsi ?: return true
      if (resolvedJava !is PsiMember) return true
      val sourcePsi = node.sourcePsi ?: return true
      return when (resolve) {
        is UField -> validate(sourcePsi, resolvedJava, holder)
        is UMethod -> if (node.javaPsi !is PsiReferenceExpression) validate(sourcePsi, resolvedJava, holder) else true
        else -> true
      }
    }

    override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
      val resolve = node.resolveToUElement()
      val resolvedJava = resolve?.javaPsi ?: return true
      if (resolvedJava !is PsiMember) return true
      val sourcePsi = node.sourcePsi ?: return true
      return validate(sourcePsi, resolvedJava, holder)
    }

    override fun visitElement(node: UElement): Boolean {
      val javaPsi = node.javaPsi ?: return true
      if (javaPsi is PsiMember && node is UDeclaration) {
        val vft = findVisibleForTestingAnnotation(javaPsi)
        if (vft != null && isDirectlyTestOnly(javaPsi)) {
          val toHighlight = node.uastAnchor.sourcePsiElement ?: return true
          holder.registerProblem(
            toHighlight,
            JvmAnalysisBundle.message("jvm.inspections.testonly.visiblefortesting"),
            RemoveAnnotationQuickFix(vft, javaPsi as PsiModifierListOwner)
          )
          return true
        }
      }
      return true
    }

    private fun validate(place: PsiElement, member: PsiMember, holder: ProblemsHolder): Boolean {
      val vft = findVisibleForTestingAnnotation(member)
      if (vft == null && !isAnnotatedAsTestOnly(member)) return true
      if (isInsideTestOnlyMethod(place) || isInsideTestOnlyField(place) || isInsideTestOnlyClass(place) || isInsideTestClass(place) ||
          isUnderTestSources(place)
      ) return false
      if (vft != null) {
        var modifier = getAccessModifierWithoutTesting(vft)
        if (modifier == null) modifier = getNextLowerAccessLevel(member)
        val modList = LightModifierList(member.manager, JavaLanguage.INSTANCE, modifier)
        if (JavaResolveUtil.isAccessible(member, member.containingClass, modList, place, null, null)) return true
      }
      reportProblem(place, member, holder)
      return false
    }

    private fun getNextLowerAccessLevel(member: PsiMember): String {
      val methodModifier = ContainerUtil.indexOf(modifierPriority) { name: String -> member.hasModifierProperty(name) }
      var minModifier = modifierPriority.size - 1
      if (member is PsiMethod) {
        for (superMethod in member.findSuperMethods()) {
          minModifier = min(minModifier, ContainerUtil.indexOf(modifierPriority) { name: String -> superMethod.hasModifierProperty(name) })
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

    private fun findVisibleForTestingAnnotation(member: PsiMember) = AnnotationUtil.findAnnotation(member, visibleForTestingAnnotations)

    private fun isInsideTestOnlyMethod(elem: PsiElement) = isAnnotatedAsTestOnly(getTopLevelParentOfType(elem, PsiMethod::class.java))

    private fun isInsideTestOnlyField(elem: PsiElement) = isAnnotatedAsTestOnly(getTopLevelParentOfType(elem, PsiField::class.java))

    private fun isInsideTestOnlyClass(elem: PsiElement) = isAnnotatedAsTestOnly(getTopLevelParentOfType(elem, PsiClass::class.java))

    private fun isAnnotatedAsTestOnly(member: PsiMember?): Boolean =
      if (member == null) false else isDirectlyTestOnly(member) || isAnnotatedAsTestOnly(member.containingClass)

    private fun isDirectlyTestOnly(member: PsiMember) =
      AnnotationUtil.isAnnotated(member, AnnotationUtil.TEST_ONLY, AnnotationUtil.CHECK_EXTERNAL)

    private fun isInsideTestClass(elem: PsiElement): Boolean {
      val parent = getTopLevelParentOfType(elem, PsiClass::class.java)
      return parent != null && TestFrameworks.getInstance().isTestClass(parent)
    }

    private fun <T : PsiElement?> getTopLevelParentOfType(e: PsiElement, c: Class<T>): T? {
      var parent = PsiTreeUtil.getParentOfType(e, c) ?: return null
      do {
        val next = PsiTreeUtil.getParentOfType(parent, c) ?: return parent
        parent = next
      }
      while (true)
    }

    private fun isUnderTestSources(elem: PsiElement): Boolean {
      val rootManger = ProjectRootManager.getInstance(elem.project)
      val file = elem.containingFile.virtualFile
      return file != null && rootManger.fileIndex.isInTestSourceContent(file)
    }

    private fun reportProblem(elem: PsiElement, target: PsiMember, holder: ProblemsHolder) {
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

  companion object {
    private val visibleForTestingAnnotations = listOf(
      "com.google.common.annotations.VisibleForTesting",
      "com.android.annotations.VisibleForTesting",
      "org.jetbrains.annotations.VisibleForTesting"
    )

    private val modifierPriority = listOf(PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE)
  }
}