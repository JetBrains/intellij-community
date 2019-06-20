// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.UnstableApiUsageInspection.Companion.DEFAULT_UNSTABLE_API_ANNOTATIONS
import com.intellij.codeInspection.util.SpecialAnnotationsUtil
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameterListOwner
import com.intellij.psi.PsiWildcardType
import com.intellij.uast.UastVisitorAdapter
import com.intellij.util.ui.FormBuilder
import com.siyeh.ig.ui.ExternalizableStringSet
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Reports declarations of classes, method and fields that in their signatures refer to a type marked with "unstable API" annotation,
 * such as `@ApiStatus.Experimental` or `@ApiStatus.Internal`, but are not marked with the same annotation on its own.
 *
 * For example, if an experimental class is used as the return type of a method, the method must also be experimental because
 * incompatible changes of the class (remove or move to another package) lead to incompatible signature changes.
 */
class UnstableTypeUsedInSignatureInspection : LocalInspectionTool() {

  @JvmField
  val unstableApiAnnotations: MutableList<String> = ExternalizableStringSet(*DEFAULT_UNSTABLE_API_ANNOTATIONS.toTypedArray())

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
    UastVisitorAdapter(UnstableTypeUsedInSignatureInspection(holder, unstableApiAnnotations.toList()), true)

  private class UnstableTypeUsedInSignatureInspection(
    private val problemsHolder: ProblemsHolder,
    private val unstableApiAnnotations: List<String>
  ) : AbstractUastNonRecursiveVisitor() {

    override fun visitClass(node: UClass): Boolean {
      if (isMarkedUnstable(node)) {
        return true
      }
      checkTypeParameters(node.javaPsi, node)
      return true
    }

    override fun visitMethod(node: UMethod): Boolean {
      if (isMarkedUnstable(node)) {
        return true
      }
      for (uastParameter in node.uastParameters) {
        if (checkReferencesUnstableType(uastParameter.type.deepComponentType, node)) {
          return true
        }
      }
      val returnType = node.returnType ?: return true
      checkReferencesUnstableType(returnType, node)

      checkTypeParameters(node.javaPsi, node)
      return true
    }

    override fun visitField(node: UField): Boolean {
      if (isMarkedUnstable(node)) {
        return true
      }
      checkReferencesUnstableType(node.type, node)
      return true
    }

    private fun checkTypeParameters(typeParameterListOwner: PsiTypeParameterListOwner, declaration: UDeclaration): Boolean {
      for (typeParameter in typeParameterListOwner.typeParameters) {
        val referencedTypes = typeParameter.extendsList.referencedTypes
        for (referencedType in referencedTypes) {
          if (checkReferencesUnstableType(referencedType, declaration)) {
            return true
          }
        }
      }
      return false
    }

    private tailrec fun isMarkedUnstable(node: UDeclaration): Boolean {
      if (node.annotations.any { it.qualifiedName in unstableApiAnnotations }) {
        return true
      }
      val containingClass = node.getContainingUClass() ?: return false
      return isMarkedUnstable(containingClass)
    }

    private fun checkReferencesUnstableType(psiType: PsiType, declaration: UDeclaration): Boolean {
      val (typeName, unstableAnnotationName) = findReferencedUnstableType(psiType.deepComponentType) ?: return false
      val message = when (declaration) {
        is UMethod -> JvmAnalysisBundle.message("jvm.inspections.unstable.type.used.in.method.signature.description", unstableAnnotationName, typeName)
        is UField -> JvmAnalysisBundle.message("jvm.inspections.unstable.type.used.in.field.signature.description", unstableAnnotationName, typeName)
        else -> JvmAnalysisBundle.message("jvm.inspections.unstable.type.used.in.class.signature.description", unstableAnnotationName, typeName)
      }
      val elementToHighlight = declaration.uastAnchor.sourcePsiElement ?: return false
      problemsHolder.registerProblem(elementToHighlight, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      return true
    }

    //Returns <class name> and <unstable annotation name>
    private fun findReferencedUnstableType(psiType: PsiType): Pair<String, String>? {
      if (psiType is PsiClassType) {
        val psiClass = psiType.resolve()
        if (psiClass != null) {
          val unstableApiAnnotation = unstableApiAnnotations.find { psiClass.hasAnnotation(it) }
          if (unstableApiAnnotation != null) {
            val className = psiClass.qualifiedName ?: psiType.className
            return className to unstableApiAnnotation
          }
        }
        for (parameterType in psiType.parameters) {
          val unstableType = findReferencedUnstableType(parameterType)
          if (unstableType != null) {
            return unstableType
          }
        }
      }
      if (psiType is PsiWildcardType) {
        return findReferencedUnstableType(psiType.extendsBound) ?: findReferencedUnstableType(psiType.superBound)
      }
      return null
    }

  }

  override fun createOptionsPanel(): JPanel {
    val annotationsListControl = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      unstableApiAnnotations,
      JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.annotations.list")
    )

    val formBuilder = FormBuilder.createFormBuilder()
    formBuilder.addComponent(annotationsListControl)

    val container = JPanel(BorderLayout())
    container.add(formBuilder.panel, BorderLayout.NORTH)
    return container
  }
}