package com.intellij.microservices.url

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.ApiStatus
import java.util.*

class UrlResolverManager(project: Project) {
  private val all: List<UrlResolver> = UrlResolverFactory.EP_NAME.extensionList.mapNotNull { it.forProject(project) }

  @ApiStatus.Internal
  val meaningfulResolvers: List<UrlResolver> = all.filter { it.javaClass.getAnnotation(HelperUrlResolver::class.java) == null }

  /**
   * @param schema schema string with `://` in the end, like `http://` or `wss://`.
   * If not specified, then all authorities should be returned.
   */
  fun getAuthorityHints(schema: String?): List<Authority.Exact>
    = all.asSequence().flatMap { it.getAuthorityHints(schema).asSequence() }.distinct().toList()

  val supportedSchemes: List<String>
    get() = all.asSequence().flatMap { it.supportedSchemes.asSequence() }.distinct().toList()

  private inline fun <T> allIterable(request: UrlResolveRequest, crossinline call: (UrlResolver) -> Sequence<T>): Iterable<T> {
    val schemeHint = listOfNotNull(request.schemeHint)
    return all.asSequence().flatMap {
      if (compatibleSchemes(it.supportedSchemes, schemeHint))
        call(it)
      else
        emptySequence()
    }.asIterable()
  }

  fun resolve(request: UrlResolveRequest, action: (UrlResolver, UrlResolveRequest) -> Iterable<UrlTargetInfo>): Iterable<UrlTargetInfo> {
    return allIterable(request) {
      action(it, request).asSequence()
    }
  }

  fun resolve(request: UrlResolveRequest): Iterable<UrlTargetInfo> = allIterable(request) { urlResolver ->
    urlResolver.resolve(request).asSequence().let { seq ->
      if (UrlPath.EMPTY != request.path && ApplicationManager.getApplication().run { isUnitTestMode || isInternal }) {
        seq.map {
          it.also { result ->
            if (result.path === request.path) {
              logger<UrlResolverManager>().error(
                "urlResolver of class ${urlResolver.javaClass} returned the UrlPath(${result.path}) identical to the requested" +
                " it is suspicious, because UrlResolvers are expected to return UrlPath built from sources, not the requested one")
            }
          }
        }
      }
      else
        seq
    }
  }

  fun getVariants(request: UrlResolveRequest): Iterable<UrlTargetInfo> = allIterable(request) { resolver ->
    resolver.getVariants().asSequence()
      .distinctBy { UrlTargetInfoDistinctKey(it) }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): UrlResolverManager {
      ThreadingAssertions.assertReadAccess()

      return UrlResolverManager(project)
    }
  }

  private class UrlTargetInfoDistinctKey(val targetInfo: UrlTargetInfo) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as UrlTargetInfoDistinctKey

      if (targetInfo.methods != other.targetInfo.methods) return false
      if (targetInfo.path != other.targetInfo.path) return false

      return true
    }

    override fun hashCode(): Int {
      return Objects.hashCode(targetInfo.path) + 31 * Objects.hashCode(targetInfo.methods)
    }
  }
}