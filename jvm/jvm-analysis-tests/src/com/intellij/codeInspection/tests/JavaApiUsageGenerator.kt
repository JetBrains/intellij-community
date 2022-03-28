package com.intellij.codeInspection.tests

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.lang.JavaVersion
import org.junit.Ignore

@Ignore
class JavaApiUsageGenerator : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(LANGUAGE_LEVEL) {
    override fun getSdk(): Sdk {
      return IdeaTestUtil.createMockJdk("java-gen", JDK_HOME)
    }
  }

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
          if (element is PsiMember) {
            val signature = LanguageLevelUtil.getSignature(element) ?: return
            if (isDocumentedSinceApi(element) && !previews.contains(signature)) {
              println(signature)
            } else if (element is PsiMethod && element.docComment == null) { // find inherited doc
              val sinceSuperVersions = element.findDeepestSuperMethods().map { superMethod ->
                val text = (superMethod.navigationElement as PsiMethod).docComment
                  ?.tags?.find { tag -> tag.name == "since" }?.valueElement?.text
                if (text != null) try { JavaVersion.parse(text)} catch (e: IllegalArgumentException) { null } else null
              }
              val sinceVersion = sinceSuperVersions.filterNotNull().minOrNull()
              if (sinceVersion == LANGUAGE_LEVEL.toJavaVersion()) {
                println(signature)
              }
            }
          }
        }

        fun isDocumentedSinceApi(element: PsiElement): Boolean = (element as? PsiDocCommentOwner)?.docComment?.tags?.any {
          tag -> tag.name == "since" && tag.valueElement?.text == SINCE_VERSION
        } ?: false
      })
      true
    }
    val srcFile = JarFileSystem.getInstance().findFileByPath("$JDK_HOME/lib/src.zip!/") ?: return
    VfsUtilCore.iterateChildrenRecursively(srcFile, VirtualFileFilter.ALL, contentIterator)
  }

  companion object {
    private const val PREVIEW_JDK_HOME = "/home/me/.jdks/openjdk-18"
    private const val JDK_HOME = "/home/me/.jdks/openjdk-18"
    private val LANGUAGE_LEVEL = LanguageLevel.JDK_18
    private const val SINCE_VERSION = "18"
  }
}