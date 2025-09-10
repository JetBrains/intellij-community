// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc.index

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.impl.source.javadoc.PsiDocFragmentName
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.GlobalSearchScope
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
  // Matches any HTML opening tag with an id attribute
  private val ID_PATTERN: Pattern = Pattern.compile("<[a-zA-Z0-9\\-]*[^>] id=[\"']?([^\"'> ]+)[\"']?[^>]*>", Pattern.CASE_INSENSITIVE)

  fun getAnchors(project: Project, fqn: String): LinkedHashSet<JavaDocFragmentData> {
    val psiClass = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.projectScope(project))
                    ?: return LinkedHashSet()

    val manager = CachedValuesManager.getManager(project)
    return manager.getCachedValue(psiClass) {
      val result = LinkedHashSet<JavaDocFragmentData>().apply {
        addAll(findIdsFromComment(psiClass.docComment))
        for (method in psiClass.methods) addAll(findIdsFromComment(method.docComment))
        for (field in psiClass.fields) addAll(findIdsFromComment(field.docComment))
      }
      Result.create(result, PsiModificationTracker.MODIFICATION_COUNT)
    }
  }

  private fun findIdsFromComment(docComment: PsiDocComment?): List<JavaDocFragmentData> {
    val text = docComment?.text ?: return listOf()
    if (" id=" !in text && " ID=" !in text) return listOf()
    val offset = docComment.textOffset
    return findIdsFromText(text)
      .map { JavaDocFragmentData(it.name, it.offset + offset) }
  }

  private fun findIdsFromText(docText: String): List<JavaDocFragmentData> {
    return ID_PATTERN.matcher(docText).results()
      .map { result ->
        val id = result.group(1)
        when (id != null && !id.isBlank()) {
          true -> JavaDocFragmentData(id, result.start(1))
          false -> null
        }
      }
      .toList()
      .filterNotNull()
  }
}

/**
 * Resolves all fragment data for the class corresponding to the given fully qualified name.
 */
fun getJavaDocFragmentsForClass(project: Project, fqn: String): LinkedHashSet<JavaDocFragmentData> {
  return project.service<JavaDocFragmentCacheService>().getAnchors(project, fqn)
}

/**
 * For a given `PsiDocFragmentName`, e.g. (`my-id` in `{@link MyClass##my-id …}`),
 * returns the fragment data and its containing class if found.
 */
fun resolveJavaDocFragment(project: Project, fragmentName: PsiDocFragmentName): Pair<PsiClass, JavaDocFragmentData>? {
  if (DumbService.getInstance(project).isDumb) return null

  val psiClass = fragmentName.getScope()
  val fqn = psiClass?.qualifiedName ?: return null

  val data = getJavaDocFragmentsForClass(project, fqn).firstOrNull { data: JavaDocFragmentData? -> data!!.name == fragmentName.text }
             ?: return null

  return psiClass to data
}