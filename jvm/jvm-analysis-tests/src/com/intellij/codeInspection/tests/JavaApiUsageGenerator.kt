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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeLines

@Ignore
class JavaApiUsageGenerator : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(LANGUAGE_LEVEL) {
    override fun getSdk(): Sdk {
      return IdeaTestUtil.createMockJdk("java-gen", JDK_HOME)
    }
  }

  /**
   * Generates removed entries. This can be useful when re-generating API lists for whatever reason. When using a modern JDK to generate the
   * API lists it will lose tagged API that got removed. This script can be used to compare new and old API lists and find removed API.
   */
  fun testGenerateRemovedEntries() {
    removedEntries(Path.of(NEW_API_DIR), Path.of(OLD_API_DIR))
  }

  private fun removedEntries(current: Path, previous: Path) {
    val result = Files.createDirectory(previous.resolve("result"))
    current.listDirectoryEntries().filter { it.name.startsWith("api") && it.extension == "txt" }.forEach { currentEntry ->
      val previousEntry = previous.resolve(currentEntry.name)
      val resultEntry = Files.createFile(result.resolve(currentEntry.name))
      val currentLines = Files.readAllLines(currentEntry)
      val previousLines = Files.readAllLines(previousEntry)
      val missingLines = previousLines.mapNotNull {  previousLine ->
        val matches = currentLines.firstOrNull { isSameSignature(it, previousLine) } != null
        if (!matches) previousLine else null
      }
      resultEntry.writeLines(missingLines)
    }
  }

  private fun isSameSignature(first: String, second: String): Boolean {
    val classFirst = first.substringBefore('#')
    val classSecond = second.substringBefore('#')
    if (classFirst != classSecond) return false
    val methodFirst = first.substringAfter('#')
    val methodSecond = first.substringAfter('#')
    val methodNameFirst = methodFirst.substringBefore('(')
    val methodNameSecond = methodSecond.substringBefore('(')
    if (methodNameFirst != methodNameSecond) return false
    val argListFirst = methodFirst.substringAfter('(').removeSuffix(")").split(',')
    val argListSecond = methodFirst.substringAfter('(').removeSuffix(")").split(',')
    if (argListFirst.size != argListSecond.size) return false
    argListFirst.forEachIndexed { i, firstArg ->
      val secondArg = argListSecond[i]
      if (firstArg != secondArg) { // check simple name if both don't match, one could be qualified and the other could be simple
        val simpleNameFirst = firstArg.substringAfterLast(".")
        val simpleNameSecond = secondArg.substringAfterLast(".")
        if (simpleNameFirst != simpleNameSecond) return false
      }
    }
    return true
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
    private const val NEW_API_DIR = "REPLACE_ME"
    private const val OLD_API_DIR = "REPLACE_ME"

    private const val PREVIEW_JDK_HOME = "/home/me/.jdks/openjdk-18"
    private const val JDK_HOME = "/home/me/.jdks/openjdk-18"
    private val LANGUAGE_LEVEL = LanguageLevel.JDK_19
    private const val SINCE_VERSION = "19"
  }
}