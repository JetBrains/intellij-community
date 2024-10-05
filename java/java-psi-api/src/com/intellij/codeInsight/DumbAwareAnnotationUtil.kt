// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight

import com.intellij.codeInsight.DumbAwareAnnotationUtil.KNOWN_ANNOTATIONS
import com.intellij.codeInsight.DumbAwareAnnotationUtil.hasAnnotation
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/**
 * Utility which helps to detect annotation in `Dumb mode`.
 */
object DumbAwareAnnotationUtil {
  private const val JAVA_LANG_PACKAGE = "java.lang"

  private val KNOWN_ANNOTATIONS = setOf(
    AnnotationUtil.NOT_NULL,
    AnnotationUtil.NULLABLE,
    AnnotationUtil.NON_NLS
  )

  /**
   * Checks if the given `PsiModifierListOwner` has an annotation with the specified fully qualified name. If the annotation has not
   * fqn (which is more likely), it will find it if and only if it is present in [KNOWN_ANNOTATIONS].
   * @param owner The `PsiModifierListOwner` instance to check for annotations.
   * @param fqn The fully qualified name of the annotation to look for.
   * @return `true` if an annotation with the given fully qualified name is present, `false` otherwise.
   */
  @JvmStatic
  fun hasAnnotation(owner: PsiModifierListOwner, fqn: String): Boolean {
    for (annotation in owner.annotations) {
      if (isAnnotationMatchesFqn(annotation, fqn)) return true
    }
    return false
  }

  /**
   * Checks if the given annotation matches with the specified fully qualified name.
   * @see hasAnnotation
   */
  @JvmStatic
  fun isAnnotationMatchesFqn(annotation: PsiAnnotation, annotationFqn: String): Boolean {
    val file = annotation.containingFile as? PsiJavaFile ?: return false
    val referenceElement = annotation.nameReferenceElement ?: return false
    if (referenceElement.isQualified && getCanonicalTextOfTheReference(referenceElement) == annotationFqn) return true
    val nameElement = referenceElement.referenceNameElement ?: return false
    val importInfo = getAnnotationImportInfo(annotationFqn)
    if (importInfo.packageName == JAVA_LANG_PACKAGE && importInfo.className == nameElement.text) return true

    return getImportedKnownAnnotations(file).contains(nameElement.text) && importInfo.className == nameElement.text
  }

  /**
   * Formats the given fully qualified name (FQN) by trimming whitespace around each segment.
   */
  @JvmStatic
  fun getFormattedReferenceFqn(referenceText: @NlsSafe String) = referenceText.split(".").joinToString(separator = ".") { pathPart -> pathPart.trim() }

  private fun getImportedKnownAnnotations(file: PsiJavaFile): Set<String> = CachedValuesManager.getCachedValue(file) {
    val importList = file.importList
                     ?: return@getCachedValue CachedValueProvider.Result(emptySet(), PsiModificationTracker.MODIFICATION_COUNT)
    val filteredAnnotations = KNOWN_ANNOTATIONS.filter { isAnnotationInImportList(it, importList) }
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

  private fun getAnnotationImportInfo(annotationFqn: String): AnnotationImportInfo {
    val packageName = StringUtil.getPackageName(annotationFqn)
    val className = StringUtil.getShortName(annotationFqn)
    return AnnotationImportInfo(packageName, className)
  }

  private fun getCanonicalTextOfTheReference(reference: PsiJavaCodeReferenceElement): String = getFormattedReferenceFqn(reference.text)

  private data class AnnotationImportInfo(val packageName: String, val className: String)
}