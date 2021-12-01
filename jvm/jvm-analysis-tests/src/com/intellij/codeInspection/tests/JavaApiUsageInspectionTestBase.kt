package com.intellij.codeInspection.tests

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature
import com.intellij.codeInspection.javaapi.JavaApiUsageInspection
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.junit.Ignore

abstract class JavaApiUsageInspectionTestBase : UastInspectionTestBase() {
  override val inspection = JavaApiUsageInspection()

  /**
   * To generate API signatures.
   * TODO exclude inheritors of ConcurrentMap#putIfAbsent
   */
  @Ignore
  fun testCollectSinceApiUsages() {
    doCollectSinceApiUsages()
  }

  private fun doCollectSinceApiUsages() {
    val previews = mutableSetOf<String>()
    val previewContentIterator = object : ContentIterator {
      override fun processFile(fileOrDir: VirtualFile): Boolean {
        val file = PsiManager.getInstance(project).findFile(fileOrDir)
        previews.addAll(
          PsiTreeUtil.findChildrenOfAnyType(file, PsiMember::class.java)
            .filter { member ->
              member.hasAnnotation(HighlightingFeature.JDK_INTERNAL_PREVIEW_FEATURE) ||
              member.hasAnnotation(HighlightingFeature.JDK_INTERNAL_JAVAC_PREVIEW_FEATURE)
            }
            .filter { member -> getLanguageLevel(member) == LANGUAGE_LEVEL }
            .mapNotNull { LanguageLevelUtil.getSignature(it) }
        )
        return true
      }

      private fun getLanguageLevel(e: PsiMember): LanguageLevel? {
        val annotation = HighlightingFeature.getPreviewFeatureAnnotation(e) ?: return null
        val feature = HighlightingFeature.fromPreviewFeatureAnnotation(annotation)
        return feature?.level
      }
    }
    if (LANGUAGE_LEVEL.isPreview) {
      val previewSrcFile = JarFileSystem.getInstance().findFileByPath("$PREVIEW_JDK_HOME/lib/src.zip!/") ?: return
      VfsUtilCore.iterateChildrenRecursively(previewSrcFile, VirtualFileFilter.ALL, previewContentIterator)
    }
    val contentIterator = ContentIterator { fileOrDir ->
      val file = PsiManager.getInstance(project).findFile(fileOrDir) as? PsiJavaFile
      file?.accept(object : JavaRecursiveElementVisitor() {
        override fun visitElement(element: PsiElement) {
          super.visitElement(element)
          if (isDocumentedSinceApi(element)) {
            val signature = LanguageLevelUtil.getSignature(element as PsiMember)
            if (!previews.contains(signature)) println(signature)
          }
        }

        fun isDocumentedSinceApi(element: PsiElement): Boolean = (element as? PsiDocCommentOwner)?.docComment?.tags?.any {
          tag -> tag.name == "since" && tag.valueElement?.text == VERSION
        } ?: false
      })
      true
    }
    val srcFile = JarFileSystem.getInstance().findFileByPath("$JDK_HOME/lib/src.zip!/") ?: return
    VfsUtilCore.iterateChildrenRecursively(srcFile, VirtualFileFilter.ALL, contentIterator)
  }

  companion object {
    private const val PREVIEW_JDK_HOME = "/home/me/.jdks/openjdk-16"
    private const val JDK_HOME = "/home/me/java/jdk-17"
    private val LANGUAGE_LEVEL = LanguageLevel.JDK_16_PREVIEW
    private const val VERSION = "17"
  }
}