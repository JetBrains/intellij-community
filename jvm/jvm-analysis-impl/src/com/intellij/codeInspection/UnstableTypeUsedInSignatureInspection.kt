// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.UnstableApiUsageInspection.Companion.DEFAULT_UNSTABLE_API_ANNOTATIONS
import com.intellij.codeInspection.util.SpecialAnnotationsUtil
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import com.intellij.psi.util.PropertyUtil
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
      if (!isAccessibleDeclaration(node) || isMarkedUnstable(node)) {
        return true
      }
      checkTypeParameters(node.javaPsi, node)
      return true
    }

    override fun visitMethod(node: UMethod): Boolean {
      if (!isAccessibleDeclaration(node) || isMarkedUnstable(node)) {
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
      if (!isAccessibleDeclaration(node) || isMarkedUnstable(node)) {
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

    private fun isAccessibleDeclaration(node: UDeclaration): Boolean {
      if (node.visibility == UastVisibility.PRIVATE) {
        if (node is UField) {
          //Kotlin properties are UField with accompanying getters\setters.
          val psiField = node.javaPsi
          if (psiField is PsiField) {
            val getter = PropertyUtil.findGetterForField(psiField)
            val setter = PropertyUtil.findSetterForField(psiField)
            return getter != null && !getter.hasModifier(JvmModifier.PRIVATE) || setter != null && !setter.hasModifier(JvmModifier.PRIVATE)
          }
        }
        return false
      }
      val containingUClass = node.getContainingUClass()
      if (containingUClass != null) {
        return isAccessibleDeclaration(containingUClass)
      }
      return true
    }

    private fun isMarkedUnstable(node: UDeclaration) = findUnstableAnnotation(node) != null

    private fun findUnstableAnnotation(node: UDeclaration): String? {
      val unstableAnnotation = node.annotations.find { it.qualifiedName in unstableApiAnnotations }
      if (unstableAnnotation != null) {
        return unstableAnnotation.qualifiedName
      }
      val containingClass = node.getContainingUClass()
      if (containingClass != null) {
        return findUnstableAnnotation(containingClass)
      }
      val containingUFile = node.getContainingUFile()
      if (containingUFile != null) {
        val packageName = containingUFile.packageName
        val psiPackage = JavaPsiFacade.getInstance(problemsHolder.project).findPackage(packageName)
        if (psiPackage != null) {
          return unstableApiAnnotations.find { psiPackage.hasAnnotation(it) }
        }
      }
      return null
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
        val uClass = psiType.resolve()?.toUElement(UClass::class.java)
        if (uClass != null) {
          val unstableApiAnnotation = findUnstableAnnotation(uClass)
          if (unstableApiAnnotation != null) {
            val className = uClass.qualifiedName ?: psiType.className
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