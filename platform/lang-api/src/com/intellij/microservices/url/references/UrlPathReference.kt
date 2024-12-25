package com.intellij.microservices.url.references

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.microservices.url.UrlPath
import com.intellij.microservices.url.UrlResolveRequest
import com.intellij.microservices.url.UrlTargetInfo
import com.intellij.microservices.utils.CommonFakeNavigatablePomTarget
import com.intellij.microservices.utils.MicroservicesUsageCollector.URL_PATH_SEGMENT_NAVIGATE_EVENT
import com.intellij.microservices.utils.MicroservicesUsageCollector.URL_PATH_VARIANTS_EVENT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.util.containers.addIfNotNull

class UrlPathReference @JvmOverloads internal constructor(
  val context: UrlPathContext,
  host: PsiElement,
  range: TextRange,
  val isAtEnd: Boolean,
  private val shouldHaveSlashBefore: Boolean = false,
  private val pathSegmentHandler: PathSegmentHandler = DefaultExactPathSegmentHandler,
  private val customNavigate: ((UrlSegmentReference) -> Unit)? = null
) : PsiReferenceBase.Poly<PsiElement>(host, range, false), UrlSegmentReference {
  
  override fun toString(): String {
    val valueIfAvailable = RecursionManager.doPreventingRecursion(this, false) { this.value } ?: "<recursive-evaluation>"
    return "URLPathReference($valueIfAvailable, $rangeInElement, contexts = ${context.resolveRequests.toList().map { it.path }.distinct()})"
  }

  //TODO: mb better to cache on modification tracker
  val unifiedPomTarget: UrlPathReferenceUnifiedPomTarget? by lazy {
    if (context.resolveRequests.none()) return@lazy null
    UrlPathReferenceUnifiedPomTarget(context, isAtEnd, element.project)
  }

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
    listOfNotNull(unifiedPomTarget)
      .map { UrlTargetInfoFakeElement(element.project, it, this, context.isDeclaration) }
      .let(PsiElementResolveResult::createResults)

  internal fun getVariantsIterator(): Iterator<LookupElement> {

    fun mkLookup(context: UrlResolveRequest,
                 exactPrefix: List<UrlPath.PathSegment>,
                 pathToComplete: List<UrlPath.PathSegment>,
                 variant: UrlTargetInfo,
                 hasSomethingNext: Boolean): LookupElement? =
      pathSegmentHandler.createLookupElement(context, exactPrefix, pathToComplete, variant,
                                             if (shouldHaveSlashBefore) "/" else "",
                                             if (isAtEnd && hasSomethingNext) "/" else "",
                                             hasSomethingNext)

    URL_PATH_VARIANTS_EVENT.log(myElement.project)

    val knownPrefixes = mutableSetOf<List<UrlPath.PathSegment>>()

    return unifiedPomTarget?.mapVariants { context, variants ->
      variants.asSequence().flatMap { variant ->

        if (!compatibleMethod(variant, context.method)) return@flatMap emptySequence<LookupElement>()

        val pathToMatch =
          if (value.isNotEmpty()) // then we ignore current segment, completion filters will filter unrelated paths after all
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
    }.orEmpty().flatten().iterator()
  }

  private fun normalizedLength(path: UrlPath): Int {
    var sub = 0
    if (path.segments.getOrNull(sub).let { it is UrlPath.PathSegment.Undefined }) {
      sub++
    }
    if (path.segments.getOrNull(sub)?.isEmpty() == true) {
      sub++
    }
    if (path.segments.size > 1) {
      sub += path.segments.takeLastWhile { it.isEmpty() }.size // ending multiple slashes
    }
    return path.segments.size - sub
  }

  private fun remainingPath(currentPath: UrlPath, variantPath: UrlPath): UrlPath {
    val chopPrefixAt = normalizedLength(currentPath).coerceAtLeast(0).coerceAtMost(variantPath.segments.size)
    val segments = variantPath.segments.subList(chopPrefixAt, variantPath.segments.size)
    val indexOfFirstValue = segments.indexOfFirst { !it.isEmpty() }
    if (indexOfFirstValue > 0) {
      return UrlPath(segments.subList(indexOfFirstValue, segments.size))
    }
    return UrlPath(segments)
  }

  override fun isReferenceTo(element: PsiElement): Boolean {
    if (element !is UrlTargetInfoFakeElement) return false

    return super.isReferenceTo(element)
  }

  companion object {
    @JvmStatic
    fun createSearchableElement(project: Project, urlPathContext: UrlPathContext): NavigatablePsiElement {
      val pomTarget = UrlPathReferenceUnifiedPomTarget(urlPathContext, project)
      return UrlTargetInfoFakeElement(project, pomTarget, null, urlPathContext.isDeclaration)
    }

    @JvmStatic
    fun createSearchableElement(project: Project, targetInfo: UrlTargetInfo): NavigatablePsiElement? {
      val urlPath = targetInfo.path.chopTrailingEmptyBlock()
      if (urlPath != UrlPath.EMPTY) {
        val searchableInfo = if (urlPath == targetInfo.path) targetInfo else UrlTargetInfoWrapper(targetInfo, urlPath)

        val urlPathContext = UrlPathContext(searchableInfo)
        if (urlPathContext.resolveRequests.any()) {
          return createSearchableElement(project, urlPathContext)
        }
      }
      return null
    }

    @JvmStatic
    fun getFromPomTargetPsiElement(psiElement: PsiElement): UrlPathReference? =
      (psiElement as? UrlTargetInfoFakeElement)?.reference
  }

  private class UrlTargetInfoWrapper(private val original: UrlTargetInfo, private val newPath: UrlPath) : UrlTargetInfo by original {
    override val path: UrlPath
      get() = newPath
  }

  private class UrlTargetInfoFakeElement(project: Project,
                                         private val unifiedPomTarget: UrlPathReferenceUnifiedPomTarget,
                                         val reference: UrlPathReference?,
                                         private val forceFindUsages: Boolean) :
    CommonFakeNavigatablePomTarget(project, unifiedPomTarget), UrlSegmentReferenceTarget {

    override fun canNavigateToSource(): Boolean = reference?.customNavigate != null || super.canNavigateToSource()

    override fun navigate(requestFocus: Boolean) {
      if (reference?.customNavigate != null) return reference.customNavigate.invoke(reference)
      if (forceFindUsages) return showFindUsages()

      URL_PATH_SEGMENT_NAVIGATE_EVENT.log(project)

      if (unifiedPomTarget.canNavigate())
        unifiedPomTarget.navigate(requestFocus)
      else
        super.navigate(requestFocus)
    }
  }

  /*
   * The class is used to fool HttpRequestVariableDocumentationProvider#getDocumentationElementForLookupItem()
   * that in case of HTTP Client returns its custom PsiElement if lookup object is String.
   * It leads to loosing all information including our documentation PsiElement.
   */
  data class UrlPathLookupObject(private val url: String) {
    override fun toString(): String = url
  }
}

private fun compatibleMethod(variant: UrlTargetInfo, method: String?): Boolean {
  if (method == null) return true
  if (variant.methods.isEmpty()) return true
  return variant.methods.contains(method)
}
