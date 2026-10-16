// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.InferredAnnotationsManager
import com.intellij.core.CoreJavaDirectoryService
import com.intellij.core.CoreJavaFileManager
import com.intellij.core.CoreJavaPsiImplementationHelper
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNameValuePair
import com.intellij.psi.impl.file.impl.JavaFileManager
import org.jetbrains.annotations.ApiStatus

/**
 * Resolves the Java project services that the frontend process does not register.
 *
 * The Java plugin registers each of these services on the backend. A frontend module shares the Java PSI code,
 * but it has no implementation for these services. Each getter returns the registered service,
 * or a default implementation.
 *
 * Call a getter here from code that a frontend module ships. Code that runs on the backend only can keep `getInstance`.
 */
@ApiStatus.Internal
object JavaOptionalServices {
  /** Returns the registered [JavaFileManager], or a new [CoreJavaFileManager] with an empty classpath. */
  @JvmStatic
  fun getJavaFileManager(project: Project): JavaFileManager {
    return JavaFileManager.getInstance(project) ?: CoreJavaFileManager(project)
  }

  /** Returns the registered [JavaPsiImplementationHelper], or a new [CoreJavaPsiImplementationHelper]. */
  @JvmStatic
  fun getJavaPsiImplementationHelper(project: Project): JavaPsiImplementationHelper {
    return JavaPsiImplementationHelper.getInstance(project) ?: CoreJavaPsiImplementationHelper(project)
  }

  @JvmStatic
  fun getJavaDirectoryService(): JavaDirectoryService {
    return JavaDirectoryService.getInstance() ?: CoreJavaDirectoryService()
  }

  /** Returns the registered [ExternalAnnotationsManager], or a manager without external annotations. */
  @JvmStatic
  fun getExternalAnnotationsManager(project: Project): ExternalAnnotationsManager {
    return ExternalAnnotationsManager.getInstance(project) ?: EmptyExternalAnnotationsManager
  }

  /** Returns the registered [InferredAnnotationsManager], or a manager that infers no annotation. */
  @JvmStatic
  fun getInferredAnnotationsManager(project: Project): InferredAnnotationsManager {
    return InferredAnnotationsManager.getInstance(project) ?: EmptyInferredAnnotationsManager
  }

  private object EmptyExternalAnnotationsManager : ExternalAnnotationsManager() {
    override fun hasAnnotationRootsForFile(file: VirtualFile): Boolean = false

    override fun findExternalAnnotation(listOwner: PsiModifierListOwner, annotationFQN: String): PsiAnnotation? = null

    override fun findExternalAnnotations(listOwner: PsiModifierListOwner, annotationFQN: String): List<PsiAnnotation> = emptyList()

    override fun isExternalAnnotationWritable(listOwner: PsiModifierListOwner, annotationFQN: String): Boolean = false

    override fun findExternalAnnotations(listOwner: PsiModifierListOwner): Array<PsiAnnotation> = PsiAnnotation.EMPTY_ARRAY

    override fun findDefaultConstructorExternalAnnotations(aClass: PsiClass): List<PsiAnnotation>? = null

    override fun findDefaultConstructorExternalAnnotations(aClass: PsiClass, annotationFQN: String): List<PsiAnnotation>? = null

    override fun annotateExternally(listOwner: PsiModifierListOwner, annotationFQName: String, fromFile: PsiFile, value: Array<PsiNameValuePair>?) {
      throw CanceledConfigurationException()
    }

    override fun deannotate(listOwner: PsiModifierListOwner, annotationFQN: String): Boolean = false

    override fun editExternalAnnotation(listOwner: PsiModifierListOwner, annotationFQN: String, value: Array<PsiNameValuePair>?): Boolean = false

    override fun chooseAnnotationsPlaceNoUi(element: PsiElement): AnnotationPlace = AnnotationPlace.IN_CODE

    override fun chooseAnnotationsPlace(element: PsiElement): AnnotationPlace = AnnotationPlace.IN_CODE

    override fun findExternalAnnotationsFiles(listOwner: PsiModifierListOwner): List<PsiFile>? = null

    override fun hasConfiguredAnnotationRoot(owner: PsiModifierListOwner): Boolean = false
  }

  private object EmptyInferredAnnotationsManager : InferredAnnotationsManager() {
    override fun findInferredAnnotation(listOwner: PsiModifierListOwner, annotationFQN: String): PsiAnnotation? = null

    override fun findInferredAnnotations(listOwner: PsiModifierListOwner): Array<PsiAnnotation> = PsiAnnotation.EMPTY_ARRAY
  }
}
