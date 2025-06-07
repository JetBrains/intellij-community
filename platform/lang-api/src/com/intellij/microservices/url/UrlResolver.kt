package com.intellij.microservices.url

import org.jetbrains.annotations.ApiStatus

interface UrlResolver {
  @Deprecated("use and implement getAuthorityHints instead", ReplaceWith("getAuthorityHints(null)"))
  @get:Deprecated("use and implement getAuthorityHints instead", ReplaceWith("getAuthorityHints(null)"))
  @get:ApiStatus.ScheduledForRemoval
  val authorityHints: List<Authority.Exact> get() = emptyList()

  /**
   * @param schema schema string with `://` in the end, like `http://` or `wss://`.
   * If not specified, then all authorities should be returned
   */
  @Suppress("DEPRECATION")
  fun getAuthorityHints(schema: String?): List<Authority.Exact> = authorityHints

  val supportedSchemes: List<String>

  fun resolve(request: UrlResolveRequest): Iterable<UrlTargetInfo>

  fun getVariants(): Iterable<UrlTargetInfo>
}

abstract class HttpUrlResolver : UrlResolver {
  companion object {
    val HTTP_AUTHORITY: List<Authority.Exact> = listOf("localhost:8080", "localhost").map(Authority::Exact)
  }

  override fun getAuthorityHints(schema: String?): List<Authority.Exact> = HTTP_AUTHORITY
  override val supportedSchemes: List<String> = HTTP_SCHEMES
}

/**
 * Annotates, that UrlResolver do not participate in resolve and completion, but provide some helping functionality.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@ApiStatus.Internal
annotation class HelperUrlResolver