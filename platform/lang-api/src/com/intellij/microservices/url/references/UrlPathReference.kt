package com.intellij.microservices.url.references

import com.intellij.microservices.HttpReferenceService
import com.intellij.microservices.url.UrlPath
import com.intellij.microservices.url.UrlTargetInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.TextRange
import com.intellij.pom.PomRenameableTarget
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus

interface UrlPathReferenceTarget : PomRenameableTarget<Any?> {
  val context: UrlPathContext

  val navigatablePsiElement: NavigatablePsiElement?

  val resolvedTargets: Set<UrlTargetInfo>

  fun toElement(reference: UrlPathReference): PsiElement
}

class UrlPathReference @JvmOverloads @ApiStatus.Internal constructor(
  val context: UrlPathContext,
  host: PsiElement,
  range: TextRange,
  val isAtEnd: Boolean,
  val shouldHaveSlashBefore: Boolean = false,
  val pathSegmentHandler: PathSegmentHandler = DefaultExactPathSegmentHandler,
  val customNavigate: ((UrlSegmentReference) -> Unit)? = null
) : PsiReferenceBase.Poly<PsiElement>(host, range, false), UrlSegmentReference {
  
  override fun toString(): String {
    val valueIfAvailable = RecursionManager.doPreventingRecursion(this, false) { this.value } ?: "<recursive-evaluation>"
    return "URLPathReference($valueIfAvailable, $rangeInElement, contexts = ${context.resolveRequests.toList().map { it.path }.distinct()})"
  }

  val unifiedPomTarget: UrlPathReferenceTarget? by lazy {
    if (context.resolveRequests.none()) return@lazy null

    service<HttpReferenceService>().createUrlPathTarget(context, isAtEnd, element.project)
  }

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    return listOfNotNull(unifiedPomTarget)
      .map { it.toElement(this) }
      .let(PsiElementResolveResult::createResults)
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

  fun remainingPath(currentPath: UrlPath, variantPath: UrlPath): UrlPath {
    val chopPrefixAt = normalizedLength(currentPath).coerceAtLeast(0).coerceAtMost(variantPath.segments.size)
    val segments = variantPath.segments.subList(chopPrefixAt, variantPath.segments.size)
    val indexOfFirstValue = segments.indexOfFirst { !it.isEmpty() }
    if (indexOfFirstValue > 0) {
      return UrlPath(segments.subList(indexOfFirstValue, segments.size))
    }
    return UrlPath(segments)
  }

  override fun isReferenceTo(element: PsiElement): Boolean {
    return service<HttpReferenceService>().isReferenceToUrlPathTarget(element)
           && super.isReferenceTo(element)
  }

  companion object {
    @JvmStatic
    fun createSearchableElement(project: Project, urlPathContext: UrlPathContext): NavigatablePsiElement {
      return service<HttpReferenceService>().createSearchableUrlElement(project, urlPathContext)
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
    fun getFromPomTargetPsiElement(psiElement: PsiElement): UrlPathReference? {
      return service<HttpReferenceService>().getUrlFromPomTargetPsi(psiElement)
    }
  }

  private class UrlTargetInfoWrapper(private val original: UrlTargetInfo, private val newPath: UrlPath) : UrlTargetInfo by original {
    override val path: UrlPath
      get() = newPath
  }

  /*
   * The class is used to fool HttpRequestVariableDocumentationProvider#getDocumentationElementForLookupItem()
   * that in case of HTTP Client returns its custom PsiElement if a lookup object is String.
   * It leads to losing all information, including our documentation PsiElement.
   */
  data class UrlPathLookupObject(private val url: String) {
    override fun toString(): String = url
  }
}