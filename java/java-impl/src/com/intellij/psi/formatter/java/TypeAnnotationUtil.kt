// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java

import com.intellij.lang.ASTNode
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.formatter.java.TypeAnnotationUtil.KNOWN_TYPE_ANNOTATIONS
import com.intellij.psi.formatter.java.TypeAnnotationUtil.isTypeAnnotation
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

/**
 * This class was designed to handle detection of type annotation during building blocks phase of Java formatter.
 * As there is no resolving available, it relies on local import table and known type annotations.
 * @see KNOWN_TYPE_ANNOTATIONS
 * @see isTypeAnnotation
 */
internal object TypeAnnotationUtil {
  private val KNOWN_TYPE_ANNOTATIONS: Set<String> = setOf(
    "org.jetbrains.annotations.NotNull",
    "org.jetbrains.annotations.Nullable"
  )

  /**
   * Checks if the given ASTNode represents a type annotation.
   *
   * @param annotation the ASTNode to check if it is a type annotation or not.
   * @return true if the ASTNode represents a type annotation, false otherwise
   */
  @JvmStatic
  fun isTypeAnnotation(annotation: ASTNode): Boolean {
    val node = annotation.psi as? PsiAnnotation ?: return false

    val languageLevel = LanguageLevelProjectExtension.getInstance(node.project).languageLevel
    if (languageLevel.isLessThan(LanguageLevel.JDK_1_8)) return false

    val next = PsiTreeUtil.skipSiblingsForward(node, PsiWhiteSpace::class.java, PsiAnnotation::class.java)
    if (next is PsiKeyword) return false
    
    val psiReference: PsiJavaCodeReferenceElement = node.nameReferenceElement ?: return false
    if (psiReference.isQualified) {
      return KNOWN_TYPE_ANNOTATIONS.contains(getCanonicalTextOfTheReference(psiReference))
    }
    else {
      val referenceName = psiReference.referenceNameElement ?: return false
      val file = psiReference.containingFile as? PsiJavaFile ?: return false
      val referenceNameText = referenceName.text
      return getImportedTypeAnnotations(file).contains(referenceNameText)
    }
  }

  private fun getImportedTypeAnnotations(file : PsiJavaFile): Set<String> = CachedValuesManager.getCachedValue(file) {
      val importList = file.importList ?: return@getCachedValue CachedValueProvider.Result(emptySet(), PsiModificationTracker.MODIFICATION_COUNT)
      val filteredAnnotations = KNOWN_TYPE_ANNOTATIONS.filter { isAnnotationInImportList(it, importList) }
        .mapNotNull { fqn -> fqn.split(".").lastOrNull() }
        .toSet()
      CachedValueProvider.Result.create(filteredAnnotations, PsiModificationTracker.MODIFICATION_COUNT)
    }

  private fun isAnnotationInImportList(annotationFqn: String, importList: PsiImportList): Boolean {
    val packageName = StringUtil.getPackageName(annotationFqn)
    return importList.importStatements.any { statement: PsiImportStatement ->
      val referenceElement = statement.importReference ?: return@any false
      val referenceElementText = getCanonicalTextOfTheReference(referenceElement)
      referenceElementText == annotationFqn || statement.isOnDemand && referenceElementText.startsWith(packageName)
    }
  }

  private fun getCanonicalTextOfTheReference(importReference: PsiJavaCodeReferenceElement): String = importReference.text.let { referenceText ->
    referenceText
      .split(".")
      .joinToString(separator = ".") { pathPart -> pathPart.trim() }
  }
}