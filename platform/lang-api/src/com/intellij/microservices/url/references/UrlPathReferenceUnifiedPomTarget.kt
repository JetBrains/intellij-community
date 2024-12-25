package com.intellij.microservices.url.references

import com.intellij.ide.presentation.Presentation
import com.intellij.microservices.MicroservicesBundle
import com.intellij.microservices.url.*
import com.intellij.microservices.url.UrlPath.Companion.FULL_PATH_VARIABLE_PRESENTATION
import com.intellij.microservices.utils.lazySynchronousResolve
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.pom.PomRenameableTarget
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement

@Presentation(provider = UrlPathSegmentPresentationProvider::class)
class UrlPathReferenceUnifiedPomTarget internal constructor(
  val context: UrlPathContext,
  private val isAtEnd: Boolean,
  project: Project) : PomRenameableTarget<Any?> {

  internal constructor(context: UrlPathContext, project: Project) : this(context, true, project)

  init {
    if (ApplicationManager.getApplication().isUnitTestMode && context.selfPaths.none()) {
      logger<UrlPathReferenceUnifiedPomTarget>()
        .error("UrlPathReferenceUnifiedPomTarget for empty contextProvider should not be created")
    }
  }

  private val urlResolver: UrlResolverManager = UrlResolverManager.getInstance(project)

  val resolvedTargets: Set<UrlTargetInfo> by lazySynchronousResolve(
    MicroservicesBundle.message("microservices.resolving.reference")) {
    val urlPathContext = if (isAtEnd) context else context.subContext(UrlPath(listOf(UrlPath.PathSegment.Undefined)))
    filterBestUrlPathMatches(
      urlPathContext.resolveRequests.asSequence().flatMap { urlResolver.resolve(it).asSequence() }.asIterable())
  }

  val pathValues: List<UrlPath> by lazy { context.resolveRequests.map { it.path } }

  internal fun <T> mapVariants(handler: (UrlResolveRequest, Iterable<UrlTargetInfo>) -> T): Sequence<T> {
    return context.resolveRequests.asSequence().map { request -> handler(request, urlResolver.getVariants(request)) }
  }

  val navigatablePsiElement: NavigatablePsiElement?
    get() {
      return resolvedTargets
        .mapNotNullTo(HashSet()) { it.resolveToPsiElement() }
        .singleOrNull()?.navigationElement
        ?.let { nav ->
          nav as? NavigatablePsiElement ?: run {
            if (ApplicationManager.getApplication().run { isInternal || isUnitTestMode }) {
              logger<UrlPathReferenceUnifiedPomTarget>().error(
                "non-navigatable navigation element: $nav resolvedTargets = $resolvedTargets")
            }
            null
          }
        }
    }

  override fun navigate(requestFocus: Boolean) {
    navigatablePsiElement?.navigate(requestFocus)
  }

  override fun setName(newName: String): Any? = null

  override fun canNavigate(): Boolean = navigatablePsiElement?.canNavigate() ?: false

  private val lastPathSegment: UrlPath.PathSegment?
    get() = pathValues.firstOrNull()?.segments?.lastOrNull()

  override fun getName(): String = FULL_PATH_VARIABLE_PRESENTATION.patternMatch(lastPathSegment ?: UrlPath.PathSegment.Undefined)

  override fun canNavigateToSource(): Boolean = navigatablePsiElement?.canNavigateToSource() ?: false

  override fun isValid(): Boolean = true

  override fun isWritable(): Boolean {
    if (lastPathSegment !is UrlPath.PathSegment.Exact) return false // path variables and properties renaming is handled by themselves
    return resolvedTargets.mapNotNull(UrlTargetInfo::resolveToPsiElement)
      .all { it.navigationElement?.isWritable ?: false }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as UrlPathReferenceUnifiedPomTarget

    //TODO: match contextProviders?
    val aSchemes = this.context.resolveRequests.mapNotNullTo(mutableSetOf()) { it.schemeHint?.takeIf { it.isNotBlank() } }
    val bSchemes = other.context.resolveRequests.mapNotNullTo(mutableSetOf()) { it.schemeHint?.takeIf { it.isNotBlank() } }
    if (!compatibleSchemes(aSchemes, bSchemes)) return false

    if (!this.pathValues.any { cur -> other.pathValues.any { cur.isCompatibleWith(it) } }) return false
    return true
  }

  override fun hashCode(): Int = this.context.selfPaths.hashCode()

  override fun toString(): String = "URLPathReferenceUnifiedPomTarget(${context.resolveRequests})"

  companion object {
    @JvmStatic
    fun getFromPomTargetPsiElement(psiElement: PsiElement): UrlPathReferenceUnifiedPomTarget? =
      (psiElement as? PomTargetPsiElement)?.target as? UrlPathReferenceUnifiedPomTarget
  }
}