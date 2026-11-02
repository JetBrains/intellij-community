// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.InferredAnnotationsManager
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
 * Gives the Java services that the Java PSI code reads also in a process without the Java plugin, such as the frontend.
 *
 * `intellij.java.psi.impl.xml` registers an open default for [JavaFileManager], [JavaPsiImplementationHelper] and
 * [JavaDirectoryService], and the Java plugin overrides each of them. `JavaCoreProjectEnvironment` and
 * `JavaCoreApplicationEnvironment` register them in code. So the getters of these services return the registered service.
 *
 * The frontend does not register [ExternalAnnotationsManager] and [InferredAnnotationsManager].
 * The getters of these services return the registered service, or a default implementation.
 *
 * Call a getter here from code that a frontend module ships. Code that runs on the backend only can keep `getInstance`.
 */
@ApiStatus.Internal
object JavaOptionalServices {
  /** Returns the registered [JavaFileManager]. */
  @JvmStatic
  fun getJavaFileManager(project: Project): JavaFileManager {
    return JavaFileManager.getInstance(project)
  }

  /** Returns the registered [JavaPsiImplementationHelper]. */
  @JvmStatic
  fun getJavaPsiImplementationHelper(project: Project): JavaPsiImplementationHelper {
    return JavaPsiImplementationHelper.getInstance(project)
  }

  /** Returns the registered [JavaDirectoryService]. */
  @JvmStatic
  fun getJavaDirectoryService(): JavaDirectoryService {
    return JavaDirectoryService.getInstance()
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
