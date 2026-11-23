// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaDocumentedElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiPackage
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.javadoc.PsiDocFragmentName
import com.intellij.psi.javadoc.PsiInlineDocTag
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import java.util.regex.Pattern

/**
 * Data associated with a JavaDoc fragment (e.g. `<p id=my-fragment-id></p>`).
 *
 * @param name fragment name
 * @param offset fragment offset in the containing file
 */
data class JavaDocFragmentData(val name: String, val offset: Int)

@Service(Service.Level.PROJECT)
private class JavaDocFragmentCacheService {
  /** Matches any HTML-like id attribute. Expected to only be found in HTML tags */
  private val ID_PATTERN: Pattern = Pattern.compile(" id=[\"']?([^\"'> ]+)[\"']?[^>]*>", Pattern.CASE_INSENSITIVE)

  /** Matches basic HTML headers. */
  private val HTML_HEADER_PATTERN: Pattern =
    Pattern.compile("<h(?<level>[1-6])[ \\S]*?>([ \\S]*?)</h\\k<level>>", Pattern.CASE_INSENSITIVE)

  /** Matches basic Markdown headers. The machine number for forward slashes is just to put a cap */
  private val MARKDOWN_HEADER_PATTERN: Pattern = Pattern.compile("/{3,8} *?#{1,6} (.*)$", Pattern.CASE_INSENSITIVE or Pattern.MULTILINE)

  fun getAnchors(project: Project, owner: PsiJavaDocumentedElement): LinkedHashSet<JavaDocFragmentData> {
    val manager = CachedValuesManager.getManager(project)
    return manager.getCachedValue(owner) {
      val result = LinkedHashSet<JavaDocFragmentData>().apply {
        if (owner is PsiClass) {
          val targetClass = getMaybeSourceClass(owner)
          addAll(findIdsFromComment(targetClass.docComment))
          for (method in targetClass.methods) addAll(findIdsFromComment(method.docComment))
          for (field in targetClass.fields) addAll(findIdsFromComment(field.docComment))
        }
        else {
          addAll(findIdsFromComment(owner.docComment))
        }
      }
      Result.create(result, PsiModificationTracker.MODIFICATION_COUNT)
    }
  }

  private fun findIdsFromComment(docComment: PsiDocComment?): List<JavaDocFragmentData> {
    val text = docComment?.text ?: return listOf()
    return buildList {
      if (docComment.isMarkdownComment) {
        addAll(this@JavaDocFragmentCacheService.findIdsFromMarkdownHeader(text, docComment.textOffset))
      }
      else {
        addAll(this@JavaDocFragmentCacheService.findIdsFromHtmlHeader(text, docComment.textOffset))
      }
      addAll(this@JavaDocFragmentCacheService.findIdsFromHtmlAttribute(text, docComment.textOffset))
      addAll(this@JavaDocFragmentCacheService.findIdsFromSystemProperties(docComment))
      addAll(this@JavaDocFragmentCacheService.findIdsFromIndexes(docComment))
    }
  }

  /** Collect usages of `{@systemProperty ...}` tags as ids since the `javadoc` tool creates ids from them */
  private fun findIdsFromSystemProperties(docComment: PsiDocComment): List<JavaDocFragmentData> {
    val properties = PsiDocComment.findInlineTagByName(docComment, "systemProperty")
    return properties.filter { it.valueElement != null }.map {
      JavaDocFragmentData(it.valueElement!!.text, it.textOffset)
    }
  }

  /** Collect usages of `{@index ...}` tags as ids since the `javadoc` tool derives ids from them */
  private fun findIdsFromIndexes(docComment: PsiDocComment): List<JavaDocFragmentData> {
    val properties = PsiDocComment.findInlineTagByName(docComment, "index")
    return properties.map<PsiInlineDocTag, JavaDocFragmentData?> {
      if (it.valueElement != null) {
        return@map JavaDocFragmentData(it.valueElement!!.text, it.textOffset)
      }
      // Due to the lexer treating `{@index "text..." desc}` as a single comment data line
      if (!it.dataElements.isEmpty()) {
        val text = it.dataElements.first().text;
        val startIndex = text.indexOf('"') + 1
        val endIndex = text.indexOf('"', startIndex)
        if (startIndex < 0 || endIndex < 0) return@map null
        return@map JavaDocFragmentData(it.dataElements.first().text.substring(startIndex, endIndex).replace(" ", ""), it.textOffset)
      }
      return@map null
    }.filterNotNull()
  }

  /** Collect usages of `id="idName"` inside the text */
  private fun findIdsFromHtmlAttribute(docText: String, offset: Int): List<JavaDocFragmentData> {
    if (" id=" !in docText && " ID=" !in docText) return listOf()
    val results = ArrayList<JavaDocFragmentData>()
    val matcher = ID_PATTERN.matcher(docText)

    while (matcher.find()) {
      val id = matcher.group(1)
      if (id != null && !id.isBlank()) {
        results.add(JavaDocFragmentData(id, offset + matcher.start(1)))
      }
    }

    return results
  }

  /** Collect usages of HTML headers (h1-6) because the `javadoc` tool derives ids from them */
  private fun findIdsFromHtmlHeader(docText: String, offset: Int): List<JavaDocFragmentData> {
    if ("<h" !in docText) return listOf()
    val results = ArrayList<JavaDocFragmentData>()
    val matcher = HTML_HEADER_PATTERN.matcher(docText)

    while (matcher.find()) {
      val id = matcher.group(2)
      // Ids override the default derived id, assuming from proper HTML
      if (id != null && !id.isBlank() && " id=" !in matcher.group() && " ID=" !in matcher.group()) {
        val normalizedId = id.replace("  ", " ").trim().replace(" ", "-").lowercase() + "-heading"
        results.add(JavaDocFragmentData(normalizedId, offset + matcher.start(2)))
      }
    }

    return results
  }

  /** Collect usages of Markdown headers `### title` because the `javadoc` tool derives ids from them */
  private fun findIdsFromMarkdownHeader(docText: String, offset: Int): List<JavaDocFragmentData> {
    if ('#' !in docText) return listOf()
    val results = ArrayList<JavaDocFragmentData>()
    val matcher = MARKDOWN_HEADER_PATTERN.matcher(docText)

    while (matcher.find()) {
      val id = matcher.group(1)
      if (id != null && !id.isBlank()) {
        val normalizedId = id.replace("  ", " ").trim().replace(" ", "-").lowercase() + "-heading"
        results.add(JavaDocFragmentData(normalizedId, offset + matcher.start(1)))
      }
    }
    return results
  }
}

/**
 * Resolves all fragment data for the class corresponding to the given [PsiClass].
 */
fun getJavaDocFragmentsForClass(project: Project, psiClass: PsiClass): LinkedHashSet<JavaDocFragmentData> {
  return project.service<JavaDocFragmentCacheService>().getAnchors(project, psiClass)
}

/** Returns the anchors in a class or a package within the search scope. */
fun getJavaDocFragments(project: Project, scope: PsiElement): LinkedHashSet<JavaDocFragmentData> {
  val cache = project.service<JavaDocFragmentCacheService>()
  //TODO maybe empty set or something ?
  return getFragmentOwner(scope)?.let { cache.getAnchors(project, it) } ?: LinkedHashSet()
}

private fun getFragmentOwner(scope: PsiElement): PsiJavaDocumentedElement? {
  return when (scope) {
    is PsiClass -> getMaybeSourceClass(scope)
    is PsiPackage -> {
      scope.directories.forEach {
        val statement = (it.findFile(PsiPackage.PACKAGE_INFO_FILE) as? PsiJavaFile)?.packageStatement
        if (statement != null) {
          return statement
        }
      }

      null
    }
    else -> null
  }
}

/**
 * For a given `PsiDocFragmentName`, e.g. (`my-id` in `{@link MyClass##my-id …}`),
 * returns the fragment data and its source declaration if found.
 */
fun resolveJavaDocFragment(project: Project, fragmentName: PsiDocFragmentName): Pair<PsiElement, JavaDocFragmentData>? {
  val scope = fragmentName.getScope() ?: return null
  return resolveJavaDocFragment(project, scope, fragmentName.text)
}

/** Returns the fragment data and its source declaration within the search scope. */
fun resolveJavaDocFragment(
  project: Project,
  scope: PsiElement,
  name: String,
): Pair<PsiElement, JavaDocFragmentData>? {
  val cache = project.service<JavaDocFragmentCacheService>()
  val owner = getFragmentOwner(scope) ?: return null
  val data = cache.getAnchors(project, owner).firstOrNull { it.name == name }
  if (data != null) return owner to data
  return null
}

/** Returns the source class if available for a given compiled class */
fun getMaybeSourceClass(clazz: PsiClass): PsiClass {
  if (clazz is ClsClassImpl) {
    val navigationClass: PsiElement = clazz.getNavigationElement()
    if (navigationClass is PsiClass) {
      return navigationClass
    }
  }
  return clazz
}