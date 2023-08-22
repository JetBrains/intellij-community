// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.StaticAnalysisAnnotationManager
import com.intellij.codeInsight.options.JavaClassValidator
import com.intellij.codeInspection.AnnotatedApiUsageUtil.findAnnotatedTypeUsedInDeclarationSignature
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.options.OptPane.stringList
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiField
import com.intellij.psi.util.PropertyUtil
import com.intellij.uast.UastVisitorAdapter
import com.siyeh.ig.ui.ExternalizableStringSet
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Reports declarations of classes, method and fields that in their signatures refer to a type marked with "unstable API" annotation,
 * such as `@ApiStatus.Experimental` or `@ApiStatus.Internal`, but are not marked with the same annotation on its own.
 *
 * For example, if an experimental class is used as the return type of a method, the method must also be experimental because
 * incompatible changes of the class (remove or move to another package) lead to incompatible signature changes.
 */
class UnstableTypeUsedInSignatureInspection : LocalInspectionTool() {

  @JvmField
  val unstableApiAnnotations: MutableList<String> =
    ExternalizableStringSet(*StaticAnalysisAnnotationManager.getInstance().knownUnstableApiAnnotations)

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (unstableApiAnnotations.none { AnnotatedApiUsageUtil.canAnnotationBeUsedInFile(it, holder.file) }) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return UastVisitorAdapter(UnstableTypeUsedInSignatureVisitor(holder, unstableApiAnnotations.toList()), true)
  }

  override fun getOptionsPane(): OptPane = pane(
    stringList("unstableApiAnnotations", JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.annotations.list"),
               JavaClassValidator().annotationsOnly())
  )
}

private class UnstableTypeUsedInSignatureVisitor(
  private val problemsHolder: ProblemsHolder,
  private val unstableApiAnnotations: List<String>
) : AbstractUastNonRecursiveVisitor() {

  override fun visitDeclaration(node: UDeclaration): Boolean {
    if (node !is UClass && node !is UMethod && node !is UField) {
      return false
    }
    if (!isAccessibleDeclaration(node) || isInsideUnstableDeclaration(node)) {
      return true
    }
    val annotatedTypeUsedInSignature = findAnnotatedTypeUsedInDeclarationSignature(node, unstableApiAnnotations) ?: return true

    val typeName = (annotatedTypeUsedInSignature.target as? PsiClass)?.qualifiedName ?: return true
    val annotationName = annotatedTypeUsedInSignature.psiAnnotation.qualifiedName ?: return true

    val message = when (node) {
      is UMethod -> JvmAnalysisBundle.message(
        "jvm.inspections.unstable.type.used.in.method.signature.description", annotationName, typeName
      )
      is UField -> JvmAnalysisBundle.message(
        "jvm.inspections.unstable.type.used.in.field.signature.description", annotationName, typeName
      )
      else -> JvmAnalysisBundle.message(
        "jvm.inspections.unstable.type.used.in.class.signature.description", annotationName, typeName
      )
    }
    problemsHolder.registerUProblem(node, message)
    return true
  }

  private fun isAccessibleDeclaration(node: UDeclaration): Boolean {
    if (node.visibility == UastVisibility.PRIVATE || node.visibility == UastVisibility.PACKAGE_LOCAL) {
      if (node is UField) {
        //Kotlin properties are private UFields with accompanying getters\setters.
        val psiField = node.javaPsi
        if (psiField is PsiField) {
          val getter = PropertyUtil.findGetterForField(psiField)?.toUElement(UMethod::class.java)
          val setter = PropertyUtil.findSetterForField(psiField)?.toUElement(UMethod::class.java)
          return getter != null && isAccessibleDeclaration(getter) || setter != null && isAccessibleDeclaration(setter)
        }
      }
      return false
    }
    val containingDeclaration = node.getParentOfType<UDeclaration>()
    if (containingDeclaration != null) {
      return isAccessibleDeclaration(containingDeclaration)
    }
    return true
  }

  private fun isInsideUnstableDeclaration(node: UDeclaration): Boolean {
    if (node.uAnnotations.any { it.qualifiedName in unstableApiAnnotations }) {
      return true
    }
    val containingClass = node.getContainingUClass()
    if (containingClass != null) {
      return isInsideUnstableDeclaration(containingClass)
    }
    val containingUFile = node.getContainingUFile()
    if (containingUFile != null) {
      val packageName = containingUFile.packageName
      val psiPackage = JavaPsiFacade.getInstance(problemsHolder.project).findPackage(packageName)
      if (psiPackage != null) {
        return unstableApiAnnotations.any { psiPackage.hasAnnotation(it) }
      }
    }
    return false
  }

}