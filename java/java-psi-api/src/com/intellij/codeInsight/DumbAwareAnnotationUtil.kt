// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight

import com.intellij.codeInsight.DumbAwareAnnotationUtil.KNOWN_ANNOTATIONS
import com.intellij.codeInsight.DumbAwareAnnotationUtil.hasAnnotation
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.java.JavaFeature
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiImportList
import com.intellij.psi.PsiImportModuleStatement
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiUtil

/**
 * Utility which helps to detect annotation in `Dumb mode`.
 */
object DumbAwareAnnotationUtil {
  private const val JAVA_LANG_PACKAGE = "java.lang"

  /**
   * Represents a list of fully qualified names for annotations that are treated as a type annotations.
   */
  private val KNOWN_ANNOTATIONS = setOf(
    AnnotationUtil.NOT_NULL,
    AnnotationUtil.NULLABLE,
    AnnotationUtil.NON_NLS,
    AnnotationUtil.J_SPECIFY_NON_NULL,
    AnnotationUtil.J_SPECIFY_NULLABLE
  )

  /**
   * Represents a mapping from a fully qualified name of a module to a set of fully qualified names of annotations
   * that are treated as type annotations and located in this module
   */
  private val KNOWN_MODULE_TO_ANNOTATIONS_MAP = mapOf(
    "org.jetbrains.annotations" to setOf(AnnotationUtil.NOT_NULL, AnnotationUtil.NULLABLE, AnnotationUtil.NON_NLS),
    "org.jspecify" to setOf(AnnotationUtil.J_SPECIFY_NON_NULL, AnnotationUtil.J_SPECIFY_NULLABLE)
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
  fun getFormattedReferenceFqn(referenceText: @NlsSafe String): String = referenceText.split(".").joinToString(separator = ".") { pathPart -> pathPart.trim() }

  private fun getImportedKnownAnnotations(file: PsiJavaFile): Set<String> = CachedValuesManager.getCachedValue(file) {
    val importList = file.importList
                     ?: return@getCachedValue CachedValueProvider.Result(emptySet(), PsiModificationTracker.MODIFICATION_COUNT)
    val filteredAnnotations = KNOWN_ANNOTATIONS.filter { isAnnotationInImportList(it, importList) || isAnnotationInModuleImportList(it, importList) }
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

  private fun isAnnotationInModuleImportList(annotationFqn: String, moduleList: PsiImportList): Boolean {
    if (!PsiUtil.isAvailable(JavaFeature.MODULE_IMPORT_DECLARATIONS, moduleList)) return false
    return moduleList.importModuleStatements.any { statement: PsiImportModuleStatement ->
      val referenceName = statement.referenceName ?: return@any false
      val formattedReferenceName = getFormattedReferenceFqn(referenceName)
      if (formattedReferenceName !in KNOWN_MODULE_TO_ANNOTATIONS_MAP) return@any false
      annotationFqn in KNOWN_MODULE_TO_ANNOTATIONS_MAP.getValue(formattedReferenceName)
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