// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.deprecation.DeprecationInspection
import com.intellij.lang.findUsages.LanguageFindUsages
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod

/**
 * Container of several values representing annotation search result.
 *
 * - [target] — starting API element of which annotation was searched.
 * - [containingDeclaration] — annotated declaration containing [target]. May be equal [target] if the [target] is annotated itself.
 * Otherwise it is the [target]'s containing class or package.
 * - [psiAnnotation] — annotation with which [containingDeclaration] is marked.
 */
@ApiStatus.Internal
data class AnnotatedContainingDeclaration(
  val target: PsiModifierListOwner,
  val containingDeclaration: PsiModifierListOwner,
  val psiAnnotation: PsiAnnotation
) {
  val isOwnAnnotation: Boolean
    get() = target == containingDeclaration

  val targetName: String
    get() = DeprecationInspection.getPresentableName(target)

  val targetType: String
    get() = LanguageFindUsages.getType(target)

  val containingDeclarationName: String
    get() = DeprecationInspection.getPresentableName(containingDeclaration)

  val containingDeclarationType: String
    get() = LanguageFindUsages.getType(containingDeclaration)

  val presentableAnnotationName: String?
    get() {
      val qualifiedName = psiAnnotation.qualifiedName ?: return null
      return qualifiedName.split('.')
               .takeLastWhile { it.isNotEmpty() && it.first().isUpperCase() }
               .joinToString(separator = ".")
               .takeIf { it.isNotEmpty() }
             ?: qualifiedName
    }
}

/**
 * Utility functions for [UnstableApiUsageInspection] and [UnstableTypeUsedInSignatureInspection].
 */
@ApiStatus.Internal
internal object AnnotatedApiUsageUtil {

  /**
   * Searches for an annotation on a [target] or its enclosing declaration (containing class or package).
   *
   * If a [target] is marked with annotation, it is returned immediately.
   * If containing class of [target] is marked with annotation, the class and its annotation is returned.
   * If the package, to which the [target] belongs, is marked with annotation, the package and its annotation is returned.
   */
  fun findAnnotatedContainingDeclaration(
    target: PsiModifierListOwner,
    annotationNames: Collection<String>,
    includeExternalAnnotations: Boolean,
    containingDeclaration: PsiModifierListOwner = target
  ): AnnotatedContainingDeclaration? {
    val annotation = AnnotationUtil.findAnnotation(containingDeclaration, annotationNames, !includeExternalAnnotations)
    if (annotation != null) {
      return AnnotatedContainingDeclaration(target, containingDeclaration, annotation)
    }
    if (containingDeclaration is PsiMember) {
      val containingClass = containingDeclaration.containingClass
      if (containingClass != null) {
        return findAnnotatedContainingDeclaration(target, annotationNames, includeExternalAnnotations, containingClass)
      }
    }
    val packageName = (containingDeclaration.containingFile as? PsiClassOwner)?.packageName ?: return null
    val psiPackage = JavaPsiFacade.getInstance(containingDeclaration.project).findPackage(packageName) ?: return null
    return findAnnotatedContainingDeclaration(target, annotationNames, includeExternalAnnotations, psiPackage)
  }

  /**
   * Searches for an annotated type that is part of the signature of the given declaration.
   *
   * * For classes, annotated type parameter is returned:
   *     * `class Foo<T extends Bar>` -> `Bar` is returned if it is annotated.
   *
   * * For methods, annotated return type, parameter type or type parameter is returned:
   *     * `public <T extends Baz> Foo method(Bar bar)` -> `Baz`, `Foo` or `Bar` is returned, whichever is annotated.
   *
   * * For fields, annotated field type is returned:
   *     * `public Foo field` -> `Foo` is returned if it is annotated.
   */
  fun findAnnotatedTypeUsedInDeclarationSignature(
    declaration: UDeclaration,
    annotations: Collection<String>
  ): AnnotatedContainingDeclaration? {
    when (declaration) {
      is UClass -> {
        return findAnnotatedTypeParameter(declaration.javaPsi, annotations)
      }
      is UMethod -> {
        for (uastParameter in declaration.uastParameters) {
          val annotatedParamType = findAnnotatedTypePart(uastParameter.type.deepComponentType, annotations)
          if (annotatedParamType != null) {
            return annotatedParamType
          }
        }
        val returnType = declaration.returnType
        if (returnType != null) {
          val annotatedReturnType = findAnnotatedTypePart(returnType.deepComponentType, annotations)
          if (annotatedReturnType != null) {
            return annotatedReturnType
          }
        }
        return findAnnotatedTypeParameter(declaration.javaPsi, annotations)
      }
      is UField -> {
        return findAnnotatedTypePart(declaration.type.deepComponentType, annotations)
      }
      else -> return null
    }
  }


  private fun findAnnotatedTypeParameter(
    typeParameterListOwner: PsiTypeParameterListOwner,
    annotations: Collection<String>
  ): AnnotatedContainingDeclaration? {
    for (typeParameter in typeParameterListOwner.typeParameters) {
      for (referencedType in typeParameter.extendsList.referencedTypes) {
        val annotatedContainingDeclaration = findAnnotatedTypePart(referencedType.deepComponentType, annotations)
        if (annotatedContainingDeclaration != null) {
          return annotatedContainingDeclaration
        }
      }
    }
    return null
  }

  private fun findAnnotatedTypePart(
    psiType: PsiType,
    annotations: Collection<String>
  ): AnnotatedContainingDeclaration? {
    if (psiType is PsiClassType) {
      val psiClass = psiType.resolve()
      if (psiClass != null) {
        val containingDeclaration = findAnnotatedContainingDeclaration(psiClass, annotations, false)
        if (containingDeclaration != null) {
          return containingDeclaration
        }
      }
      for (parameterType in psiType.parameters) {
        val parameterResult = findAnnotatedTypePart(parameterType, annotations)
        if (parameterResult != null) {
          return parameterResult
        }
      }
    }
    if (psiType is PsiWildcardType) {
      return findAnnotatedTypePart(psiType.extendsBound, annotations) ?: findAnnotatedTypePart(psiType.superBound, annotations)
    }
    return null
  }
}