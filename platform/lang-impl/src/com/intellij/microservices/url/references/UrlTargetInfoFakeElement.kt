// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.url.references

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.ide.presentation.Presentation
import com.intellij.microservices.url.UrlPath
import com.intellij.microservices.url.UrlResolveRequest
import com.intellij.microservices.url.UrlResolverManager
import com.intellij.microservices.url.UrlTargetInfo
import com.intellij.microservices.url.parameters.*
import com.intellij.microservices.utils.CommonFakeNavigatablePomTarget
import com.intellij.microservices.utils.MicroservicesUsageCollector.URL_PATH_SEGMENT_NAVIGATE_EVENT
import com.intellij.microservices.utils.MicroservicesUsageCollector.URL_PATH_VARIANTS_EVENT
import com.intellij.microservices.utils.SimpleNamePomTarget
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.pom.PomTarget
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiElement
import com.intellij.semantic.SemKey
import com.intellij.util.asSafely
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.annotations.TestOnly

internal class UrlTargetInfoFakeElement(
  project: Project,
  private val unifiedPomTarget: UrlPathReferenceUnifiedPomTarget,
  val reference: UrlPathReference?,
  private val forceFindUsages: Boolean,
) : CommonFakeNavigatablePomTarget(project, unifiedPomTarget), UrlSegmentReferenceTarget {

  override fun canNavigateToSource(): Boolean = reference?.customNavigate != null || super.canNavigateToSource()

  override fun navigate(requestFocus: Boolean) {
    val customNavigate = reference?.customNavigate
    if (customNavigate != null) {
      return customNavigate.invoke(reference)
    }

    if (forceFindUsages) return showFindUsages()

    URL_PATH_SEGMENT_NAVIGATE_EVENT.log(project)

    if (unifiedPomTarget.canNavigate())
      unifiedPomTarget.navigate(requestFocus)
    else
      super.navigate(requestFocus)
  }
}

private fun compatibleMethod(variant: UrlTargetInfo, method: String?): Boolean {
  if (method == null) return true
  if (variant.methods.isEmpty()) return true
  return variant.methods.contains(method)
}

internal fun UrlPathReference.getVariantsIterator(): Iterator<LookupElement> {

  fun mkLookup(
    context: UrlResolveRequest,
    exactPrefix: List<UrlPath.PathSegment>,
    pathToComplete: List<UrlPath.PathSegment>,
    variant: UrlTargetInfo,
    hasSomethingNext: Boolean,
  ): LookupElement? =
    pathSegmentHandler.createLookupElement(context, exactPrefix, pathToComplete, variant,
                                           if (shouldHaveSlashBefore) "/" else "",
                                           if (isAtEnd && hasSomethingNext) "/" else "",
                                           hasSomethingNext)

  URL_PATH_VARIANTS_EVENT.log(element.project)

  val knownPrefixes = mutableSetOf<List<UrlPath.PathSegment>>()

  return (unifiedPomTarget as? UrlPathReferenceUnifiedPomTarget)
    ?.mapVariants { context, variants ->
      variants.asSequence().flatMap { variant ->
        if (!compatibleMethod(variant, context.method)) return@flatMap emptySequence<LookupElement>()

        val pathToMatch =
          if (value.isNotEmpty()) // then we ignore the current segment, completion filters will filter unrelated paths after all
            context.path.segments.let { UrlPath(it.subList(0, (it.size - 1).coerceAtLeast(0))) }
          else context.path

        if (!pathToMatch.canBePrefixFor(variant.path)) return@flatMap emptySequence<LookupElement>()
        val pathToComplete = remainingPath(pathToMatch, variant.path)
        val exactPrefix = pathSegmentHandler.getExactPrefix(pathToComplete)

        knownPrefixes.add(exactPrefix)
        val prefixes = when {
          isAtEnd -> exactPrefix.indices.map { i -> exactPrefix.subList(0, i) }.filter { knownPrefixes.add(it) }
          exactPrefix.isNotEmpty() -> listOf(exactPrefix.subList(0, 1))
          else -> emptyList()
        }

        val results = prefixes.mapNotNullTo(ArrayList(prefixes.size + 1)) { prefix ->
          mkLookup(context, prefix, pathToComplete.segments, variant, true)
        }
        if (isAtEnd)
          results.addIfNotNull(
            mkLookup(context, exactPrefix, pathToComplete.segments, variant, exactPrefix.size != pathToComplete.segments.size))
        results.asSequence()
      }
    }
    .orEmpty()
    .flatten()
    .iterator()
}

internal fun getAvailableAuthorities(project: Project, schema: String?): List<AuthorityPomTarget> =
  UrlResolverManager.getInstance(project).getAuthorityHints(schema).map { AuthorityPomTarget(it.text) }

internal class AuthorityReferenceFakeElement(
  project: Project, unifiedPomTarget: AuthorityPomTarget,
  private val customNavigate: (() -> Unit)?,
) : CommonFakeNavigatablePomTarget(project, unifiedPomTarget), UrlSegmentReferenceTarget {

  override fun canNavigateToSource(): Boolean = customNavigate != null || super.canNavigateToSource()

  override fun navigate(requestFocus: Boolean) {
    if (customNavigate != null) return customNavigate.invoke()
    URL_PATH_SEGMENT_NAVIGATE_EVENT.log(project)

    super.navigate(requestFocus)
  }
}

@Presentation(provider = AuthorityPresentationProvider::class)
internal class AuthorityPomTarget(text: String) : SimpleNamePomTarget(text)

private class QueryParameterInfoFakeElement(
  project: Project,
  private val queryParameterPomTarget: QueryParameterNamePomTarget,
  private val forceFindUsages: Boolean
) : CommonFakeNavigatablePomTarget(project, queryParameterPomTarget), UrlSegmentReferenceTarget {

  override fun navigate(requestFocus: Boolean) {
    if (forceFindUsages) return showFindUsages()
    if (queryParameterPomTarget.canNavigate())
      queryParameterPomTarget.navigate(requestFocus)
    else
      super.navigate(requestFocus)
  }
}

@Presentation(provider = QueryParameterPresentationProvider::class)
class QueryParameterNamePomTarget(
  val urlPathReferenceUnifiedPomTarget: UrlPathReferenceUnifiedPomTarget,
  private val project: Project,
  name: String
) : SimpleNamePomTarget(name), QueryParameterNameTarget {

  val navigatablePsiElement: PsiElement?
    get() = this.paramNavigatable ?: urlPathReferenceUnifiedPomTarget.navigatablePsiElement

  val paramNavigatable: PsiElement?
    get() {
      val resolvedParameters = urlPathReferenceUnifiedPomTarget.resolvedTargets.asSequence()
        .flatMap { t -> t.queryParameters.asSequence().filter { it.name == name } }

      val paramNavigatable = resolvedParameters
        .mapNotNull { it.resolveToPsiElement() }
        .firstOrNull()
      return paramNavigatable
    }

  override fun navigate(requestFocus: Boolean) {
    val psiElement = navigatablePsiElement

    if (psiElement is Navigatable && psiElement.canNavigate())
      psiElement.navigate(requestFocus)
    else
      urlPathReferenceUnifiedPomTarget.navigate(requestFocus)
  }

  override fun canNavigate(): Boolean = urlPathReferenceUnifiedPomTarget.canNavigate()

  override fun equals(other: Any?): Boolean {
    if (!super.equals(other)) return false
    other as QueryParameterNamePomTarget
    return urlPathReferenceUnifiedPomTarget == other.urlPathReferenceUnifiedPomTarget
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + urlPathReferenceUnifiedPomTarget.hashCode()
    return result
  }

  override fun toElement(forceFindUsagesOnNavigate: Boolean): PsiElement {
    return QueryParameterInfoFakeElement(project, this, forceFindUsagesOnNavigate)
  }
}

object QueryParameterSemElementSupport : RenameableSemElementSupport<QueryParameterSem> {
  private fun createQueryParameterInfoPomTargetElement(project: Project,
                                                       context: UrlPathContext,
                                                       name: String,
                                                       forceFindUsages: Boolean): PomTargetPsiElement {
    val pomTarget = QueryParameterNamePomTarget(UrlPathReferenceUnifiedPomTarget(context, project), project, name)
    return QueryParameterInfoFakeElement(project, pomTarget, forceFindUsages)
  }

  override fun findReferencingPsiElements(pomTarget: PomTarget): Iterable<PsiElement> =
    pomTarget.asSafely<QueryParameterNamePomTarget>()
      ?.paramNavigatable
      ?.takeIf { getSemElement(it) != null }
      ?.let { listOf(it) } ?: emptyList()

  override fun supportsTarget(pomTarget: PomTarget): Boolean = pomTarget is QueryParameterNamePomTarget

  override fun createPomTargetPsi(project: Project, sem: QueryParameterSem): PomTargetPsiElement =
    createQueryParameterInfoPomTargetElement(
      project,
      sem.urlPathContext,
      sem.name,
      true
    )

  override val SEM_KEY: SemKey<QueryParameterSem>
    get() = QUERY_PARAMETER_SEM_KEY

  @TestOnly
  @JvmStatic
  fun getNavigatablePsiElement(reference: QueryParameterNameReference): PsiElement? =
    reference.resolve().asSafely<QueryParameterInfoFakeElement>()
      ?.target.asSafely<QueryParameterNamePomTarget>()
      ?.navigatablePsiElement
}