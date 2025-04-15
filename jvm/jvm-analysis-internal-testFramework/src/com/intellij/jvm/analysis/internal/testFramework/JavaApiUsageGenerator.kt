package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.java.codeserver.core.JavaPreviewFeatureUtil
import com.intellij.jvm.analysis.internal.testFramework.JavaApiUsageGenerator.Companion.JDK_HOME
import com.intellij.jvm.analysis.internal.testFramework.JavaApiUsageGenerator.Companion.LANGUAGE_LEVEL
import com.intellij.jvm.analysis.internal.testFramework.JavaApiUsageGenerator.Companion.PREVIEW_JDK_HOME
import com.intellij.jvm.analysis.internal.testFramework.JavaApiUsageGenerator.Companion.SINCE_VERSION
import com.intellij.openapi.module.JdkApiCompatabilityCache
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.lang.JavaVersion
import com.intellij.workspaceModel.ide.legacyBridge.sdk.SdkTableImplementationDelegate
import org.junit.Ignore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Generator which is used to generate required api files for [com.intellij.codeInspection.JavaApiUsageInspection].
 * To generate new API lists for next release, you will need to set [JDK_HOME], [LANGUAGE_LEVEL], [SINCE_VERSION] and then run
 * [testCollectSinceApiUsages].
 */
@Ignore
class JavaApiUsageGenerator : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(LANGUAGE_LEVEL) {
    override fun getSdk(): Sdk {
      val sdk = SdkTableImplementationDelegate.getInstance().createSdk("java-gen", JavaSdk.getInstance(), JDK_HOME)
      JavaSdk.getInstance().setupSdkPaths(sdk)
      return sdk
    }
  }

  /**
   * Generates removed entries. This can be useful when re-generating API lists for whatever reason. When using a modern JDK to generate the
   * API lists, it will lose the tagged API that got removed. This script can be used to compare new and old API lists and find removed API.
   */
  fun testGenerateRemovedEntries() {
    IdeaTestUtil.withLevel(myFixture.module, LANGUAGE_LEVEL) {
      removedEntries(Path.of(TEMP_API_DIR), Path.of(API_DIR))
    }
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
      if (firstArg != secondArg) { // check simple name if both don't match, one could be qualified, and the other could be simple
        val simpleNameFirst = firstArg.substringAfterLast(".")
        val simpleNameSecond = secondArg.substringAfterLast(".")
        if (simpleNameFirst != simpleNameSecond) return false
      }
    }
    return true
  }

  private fun PsiMember.isPublicApi(): Boolean {
    if (modifierList?.hasModifierProperty(PsiModifier.PRIVATE) == true) return false
    val parentMember = parentOfType<PsiMember>() ?: return true
    return parentMember.isPublicApi()
  }

  fun testRemoveNonPublicApi() {
    IdeaTestUtil.withLevel(myFixture.module, LANGUAGE_LEVEL) {
      filterSignatures(Path.of(API_DIR)) { isPublicApi() }
    }
  }

  /**
   * Can be used to filter out API lists from the [path] according to the provided [filter].
   */
  private fun filterSignatures(path: Path, filter: PsiMember.() -> Boolean) {
    path.listDirectoryEntries().filter { it.name.startsWith("api") && it.extension == "txt" }.forEach { currentEntry ->
      val apiFile = path.resolve(currentEntry.name)
      val validSignatures = mutableListOf<String>()
      apiFile.readLines().forEach { signature ->
        val member = findMember(signature)
        if (member == null) {
          validSignatures.add(signature) // we still add it to signatures because it might just not be present in the current working JDK
          println("Could not find member for $signature")
          return@forEach
        }
        if (member.filter()) {
          validSignatures.add(signature)
        }
      }
      apiFile.writeLines(validSignatures)
    }
  }

  private fun findMember(signature: String): PsiMember? {
    val clazz = JavaPsiFacade.getInstance(project).findClass(signature.substringBefore("#"), GlobalSearchScope.allScope(project))
    if (clazz == null) return null
    return if (signature.contains("#")) {
      if (signature.contains("(")) {
        val methods = clazz.findMethodsByName(signature.substringAfter("#").substringBefore("("), true)
        if (methods.isEmpty()) return null
        val paramFqns = getParamFqns(signature)
        val method = methods.firstOrNull { method ->
          if (method.parameterList.parametersCount != paramFqns.size) return@firstOrNull false
          method.getSignature(PsiSubstitutor.EMPTY).getParameterTypes().zip(paramFqns).all { (paramType, sigTypeCanonicalText) ->
            paramType.canonicalText == sigTypeCanonicalText
          }
        }
        method
      } else {
        val field = clazz.findFieldByName(signature.substringAfter("#"), true)
        field
      }
    } else clazz
  }

  fun testCollectSinceApiUsages() {
    IdeaTestUtil.withLevel(myFixture.module, LANGUAGE_LEVEL) {
      doCollectSinceApiUsages()
    }
  }

  fun getParamFqns(signature: String): List<String> {
    return signature.substringAfter("(").substringBefore(")").split(";").dropLast(1)
  }

  /**
   * Run to generate API lists.
   * Setting [LANGUAGE_LEVEL], [SINCE_VERSION] and [JDK_HOME] or [PREVIEW_JDK_HOME] is required.
   */
  private fun doCollectSinceApiUsages() {
    val previews = mutableSetOf<String>()
    val previewContentIterator = object : ContentIterator {
      override fun processFile(fileOrDir: VirtualFile): Boolean {
        val file = PsiManager.getInstance(project).findFile(fileOrDir)
        previews.addAll(
          PsiTreeUtil.findChildrenOfAnyType(file, PsiMember::class.java)
            .filter { member ->
              member.hasAnnotation(JavaPreviewFeatureUtil.JDK_INTERNAL_PREVIEW_FEATURE) ||
              member.hasAnnotation(JavaPreviewFeatureUtil.JDK_INTERNAL_JAVAC_PREVIEW_FEATURE)
            }
            .filter { member -> getLanguageLevel(member) == LANGUAGE_LEVEL }
            .mapNotNull { JdkApiCompatabilityCache.getInstance().getSignature(it) }
        )
        return true
      }

      private fun getLanguageLevel(e: PsiMember): LanguageLevel? {
        val annotation = JavaPreviewFeatureUtil.getPreviewFeatureAnnotation(e)
                         ?: return null
        val feature = JavaPreviewFeatureUtil.fromPreviewFeatureAnnotation(annotation)
        return feature?.minimumLevel
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
          if (element is PsiMember && element.isPublicApi()) {
            val signature = JdkApiCompatabilityCache.getInstance().getSignature(element) ?: return
            val className = signature.substringBefore("#")
            if (JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project)) == null) {
              return // If the class is not in all scope, don't generate
            }
            val paramFqns = getParamFqns(signature).map { name -> name.substringBefore("[").substringBefore("<") }
            if (paramFqns.any { name -> !isValidTypeName(element, name) }) {
              throw IllegalStateException("Generated parameters $paramFqns must be fully qualified or primitive")
            }
            if (isDocumentedSinceApi(element) && !previews.contains(signature)) {
              println(signature)
            } else if (element is PsiMethod && element.docComment == null) { // find inherited doc
              val sinceSuperVersions = element.findDeepestSuperMethods().map { superMethod ->
                val text = (superMethod.navigationElement as PsiMethod).docComment
                  ?.tags?.find { tag -> tag.name == "since" }?.valueElement?.text
                if (text != null) try { JavaVersion.parse(text)} catch (_: IllegalArgumentException) { null } else null
              }
              val sinceVersion = sinceSuperVersions.filterNotNull().minOrNull()
              if (sinceVersion == LANGUAGE_LEVEL.toJavaVersion()) {
                println(signature)
              }
            }
          }
        }

        fun isDocumentedSinceApi(element: PsiElement): Boolean = (element as? PsiDocCommentOwner)?.docComment?.tags?.any { tag ->
          tag.name == "since" && tag.valueElement?.text == SINCE_VERSION
        } == true
      })
      true
    }
    val srcFile = JarFileSystem.getInstance().findFileByPath("$JDK_HOME/lib/src.zip!/") ?: return
    VfsUtilCore.iterateChildrenRecursively(srcFile, VirtualFileFilter.ALL, contentIterator)
  }

  private fun isValidTypeName(element: PsiMember, name: String): Boolean {
    if (name in PsiTypes.primitiveTypes().map(PsiPrimitiveType::getName)) return true
    if (isValidTypeArgName(element, name)) return true
    return name.contains(".")
  }

  private fun isValidTypeArgName(element: PsiMember, name: String): Boolean {
    if (element is PsiTypeParameterListOwner && name in element.typeParameters.map { it.name }) return true
    val parent = element.parentOfType<PsiMember>() ?: return false
    return isValidTypeName(parent, name)
  }

  companion object {
    private const val TEMP_API_DIR = "REPLACE_ME"

    /**
     * Dir to API lists
     */
    private const val API_DIR = "REPLACE_ME"

    private const val PREVIEW_JDK_HOME = "/home/me/.jdks/openjdk-20"

    private const val JDK_HOME = "/home/me/.jdks/openjdk-20"

    /**
     * The language level to check for.
     */
    private val LANGUAGE_LEVEL = LanguageLevel.JDK_24

    /**
     * The @since tag value used should match [LANGUAGE_LEVEL].
     */
    private const val SINCE_VERSION = "24"
  }
}