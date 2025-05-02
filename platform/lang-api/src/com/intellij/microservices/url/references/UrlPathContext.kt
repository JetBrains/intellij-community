package com.intellij.microservices.url.references

import com.intellij.microservices.url.*
import com.intellij.microservices.utils.LazyChain
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.lazyPub
import org.jetbrains.annotations.ApiStatus

class UrlPathContext private constructor(
  private val immediate: LazyChain.Immediate<UrlPathContextData>,
  private val delayed: LazyChain<UrlPathContextData>?,
) {

  private constructor(data: UrlPathContextData) : this(LazyChain.Immediate(data), null)

  private val genuineChain: LazyChain<UrlPathContextData> get() = delayed ?: immediate

  val parent: UrlPathContext? get() = immediate.value.parent?.context

  private data class Info(
    val schemes: Set<String>,
    val authorities: Set<String>,
    val methods: Set<String>,
    val contentTypes: Set<String>,
    val isDeclaration: Boolean,
    val fullUrlComputation: (() -> UrlPathContext?)? = null
  ) {
    init {
      if (ApplicationManager.getApplication().run { isUnitTestMode || isInternal }) {
        logger<UrlPathContext>().assertTrue(schemes.none { it.isBlank() }, "blank scheme looks like an error")
      }
    }

    private fun Collection<String>.orNull(): Collection<String?> = this.takeIf { it.isNotEmpty() } ?: listOf(null)
    fun toUrlResolveRequestsStubs(paths: Sequence<UrlPath>): Sequence<UrlResolveRequest> =
      // that feeling when there is no for-comprehensions in Kotlin
      schemes.orNull().asSequence().flatMap { scheme ->
        authorities.orNull().asSequence().flatMap { authority ->
          methods.orNull().asSequence().flatMap { method ->
            paths.map { path ->
              UrlResolveRequest(scheme, authority, path, method)
            }
          }
        }
      }

    override fun toString(): String =
      "Info(schemes=$schemes, authorities=$authorities, methods=$methods, contentTypes=$contentTypes, isDeclaration=$isDeclaration)"
  }

  private data class UrlPathContextData(
    val info: Info,
    val paths: List<UrlPath>,
    val parent: UrlPathContextData?) {

    init {
      if (ApplicationManager.getApplication().run { isUnitTestMode || isInternal }) {
        logger<UrlPathContext>().assertTrue(paths.isNotEmpty(), "paths list should contain at least UrlPath.EMPTY")
      }
    }

    val context: UrlPathContext by lazyPub { UrlPathContext(this) }

    fun isEmpty(): Boolean = paths.singleOrNull() == UrlPath.EMPTY && parent?.isEmpty() != false
  }

  private fun isEvaluated(): Boolean = this.delayed == null

  /**
   * Delays expensive computation until it is really necessary (for instance on [UrlPathReference] resolve).
   * @see fullyEvaluated
   */
  fun applyOnResolve(trans: UrlPathContext.() -> UrlPathContext): UrlPathContext =
    UrlPathContext(this.immediate, this.genuineChain.chainLazy {
      logger<UrlPathContext>().assertTrue(canBuildUrlContext.get(), "expensive transformations shouldn't be called there")
      trans.invoke(it.context).genuineChain.value
    })

  /**
   * Provides full url information for generation from [UrlPathContext]
   *
   * This method is a workaround for HttpClient.
   * Right now, we can't provide full url information from a resolved version of [UrlPathContext]
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  fun computeFullUrlInfo(): UrlPathContext {
    logger<UrlPathContext>().assertTrue(canBuildUrlContext.get(), "expensive transformations shouldn't be called there")
    return info.fullUrlComputation?.invoke() ?: this
  }

  private fun update(transformation: UrlPathContextData.() -> UrlPathContextData): UrlPathContext {
    return UrlPathContext(this.immediate.chain(transformation), this.delayed?.chain(transformation))
  }

  /**
   * Computes all delayed by [applyOnResolve] computations. Could be expensive. Please avoid calling it during highlighting or on EDT.
   * @see forbidExpensiveUrlContext
   */
  val fullyEvaluated: UrlPathContext
    get() {
      logger<UrlPathContext>().assertTrue(canBuildUrlContext.get(), "expensive transformations shouldn't be called there")
      return if (isEvaluated()) this else genuineChain.value.context
    }

  override fun toString(): String {
    return with(immediate.value) {
      val pathsBlocks = sequenceOf(paths) + generateSequence(parent) { it.parent }.map { it.paths }
      "UrlPathContext(${
        pathsBlocks.joinToString(" <- ") { block ->
          block.map { it.toStringWithStars() }.distinct().joinToString(", ", "[", "]")
        }
      }, $info)"
    }
  }

  constructor(contexts: Iterable<UrlResolveRequest>)
    : this(UrlPathContextData(
    Info(contexts.mapNotNullTo(LinkedHashSet()) { it.schemeHint },
         contexts.mapNotNullTo(LinkedHashSet()) { it.authorityHint },
         contexts.mapNotNullTo(LinkedHashSet()) { it.method },
         emptySet(),
         false),
    contexts.map { it.path }.distinct(),
    null))

  constructor(target: UrlTargetInfo)
    : this(UrlPathContextData(Info(target.schemes.toSet(),
                                   target.authorities.filterIsInstance<Authority.Exact>().map { it.text }.toSet(),
                                   target.methods,
                                   target.contentTypes,
                                   false),
                              listOf(target.path), null))

  fun subContext(subPath: UrlPath): UrlPathContext =
    update { UrlPathContextData(info, listOf(subPath), this) }

  fun subContexts(subContexts: List<UrlPath>): UrlPathContext =
    if (subContexts.isEmpty()) this
    else update { UrlPathContextData(info, subContexts, this) }

  fun withMethod(httpMethod: String?): UrlPathContext =
    update { UrlPathContextData(info.copy(methods = listOfNotNull(httpMethod).toSet()), paths, parent) }

  fun withMethods(httpMethods: Collection<String>): UrlPathContext =
    update { UrlPathContextData(info.copy(methods = httpMethods.toSet()), paths, parent) }

  fun withSchemes(schemes: Set<String>): UrlPathContext =
    update { UrlPathContextData(info.copy(schemes = schemes), paths, parent) }

  fun withAuthorities(authorities: Set<String>): UrlPathContext =
    update { UrlPathContextData(info.copy(authorities = authorities), paths, parent) }

  fun withDeclarationFlag(isDeclaration: Boolean): UrlPathContext =
    update { UrlPathContextData(info.copy(isDeclaration = isDeclaration), paths, parent) }

  fun withPaths(paths: List<UrlPath>): UrlPathContext = update { UrlPathContextData(info, paths.distinct(), parent) }

  fun withContentTypes(contentTypes: Set<String>): UrlPathContext = update {
    UrlPathContextData(info.copy(contentTypes = contentTypes), paths, parent)
  }

  fun withoutLastAppendedText(): UrlPathContext =
    update {
      UrlPathContextData(info, this.parent?.paths ?: listOf(UrlPath.EMPTY), this.parent?.parent)
    }

  fun withFullUrlComputation(fullUrlComputation: () -> UrlPathContext?): UrlPathContext =
    update { UrlPathContextData(info.copy(fullUrlComputation = fullUrlComputation), paths, parent) }

  fun isEmpty(): Boolean = immediate.value.isEmpty()

  val selfPaths: List<UrlPath> get() = immediate.value.paths

  private val info: Info get() = this.immediate.value.info

  val authorities: List<String>
    get() = info.authorities.toList()

  val schemes: List<String>
    get() = info.schemes.toList()

  val methods: List<String>
    get() = info.methods.toList()

  val isDeclaration: Boolean
    get() = info.isDeclaration

  val contentTypes: Set<String>
    get() = info.contentTypes

  val resolveRequests: Iterable<UrlResolveRequest>
    get() = with(this.fullyEvaluated.immediate.value) {
      val pathBlocks = generateSequence(this) { it.parent }.map { it.paths }.toList().reversed()
      val paths = pathBlocks.reduce { roots, subs ->
        roots.asSequence().flatMap { root ->
          subs.asSequence().map { subSegments ->
            UrlPath(ContainerUtil.concat(chopTrailingEmptyBlock(root.segments), chopLeadingEmptyBlock(subSegments.segments)))
          }
        }.toList()
      }

      info.toUrlResolveRequestsStubs(paths.asSequence()).asIterable()
    }

  companion object {
    @JvmStatic
    fun singleContext(scheme: String?, authority: String?, urlPath: UrlPath): UrlPathContext =
      UrlPathContext(listOf(UrlResolveRequest(scheme, authority, urlPath)))

    @JvmStatic
    @JvmOverloads
    fun supportingSchemes(schemes: List<String>, method: String? = null): UrlPathContext =
      when {
        schemes.isNotEmpty() -> UrlPathContext(schemes.map { UrlResolveRequest(it, null, UrlPath.EMPTY, method) })
        method != null -> emptyRoot().withMethod(method)
        else -> emptyRoot()
      }

    @JvmStatic
    fun emptyRoot(): UrlPathContext = singleContext(null, null, UrlPath.EMPTY)
  }
}

/**
 * @see com.intellij.microservices.url.references.UrlPathReferenceUnifiedPomTarget.resolvedTargets
 */
fun UrlPathContext.resolveTargets(project: Project): Set<UrlTargetInfo> {
  val urlResolver = UrlResolverManager.getInstance(project)

  return filterBestUrlPathMatches(
    resolveRequests.asSequence().flatMap { urlResolver.resolve(it).asSequence() }.asIterable())
}

fun UrlPathContext.applyFromParsed(parsedUrl: UrlPksParser.ParsedPksUrl,
                                   scheme: Boolean = true,
                                   auth: Boolean = true,
                                   path: Boolean = true): UrlPathContext {
  var result = this
  val authorityValue = parsedUrl.authority?.valueIfKnown?.takeIf { it.isNotEmpty() }
  val schemeValue = parsedUrl.scheme?.valueIfKnown?.takeIf { it.isNotEmpty() && it != authorityValue }
  val urlPath = parsedUrl.urlPath

  if (scheme) result = schemeValue?.let { result.withSchemes(setOf(it)) } ?: result
  if (auth) result = authorityValue?.let { result.withAuthorities(setOf(it)) } ?: result
  if (path) result = urlPath.let { result.subContext(it) }

  return result
}

fun UrlPathContext.configureFromStringHeuristically(url: String?, specification: FrameworkUrlPathSpecification): UrlPathContext {
  if (url == null) return this
  val pks = PartiallyKnownString(url)
  var pathContext = this
  val fullUrl = specification.parser.parseFullUrl(pks)
  if (!fullUrl.authority?.valueIfKnown.isNullOrEmpty()) {
    pathContext = pathContext.applyFromParsed(fullUrl)
  }
  else {
    pathContext = pathContext.subContext(specification.parser.parseUrlPath(pks).urlPath)
  }
  return pathContext
}

private val canBuildUrlContext: ThreadLocal<Boolean> = ThreadLocal.withInitial { true }

fun <T> forbidExpensiveUrlContext(call: () -> T): T {
  val prev = canBuildUrlContext.get()
  try {
    canBuildUrlContext.set(false)
    return call()
  }
  finally {
    canBuildUrlContext.set(prev)
  }

}

fun UrlPath.chopLeadingEmptyBlock(): UrlPath =
  if (this !== UrlPath.EMPTY && segments.firstOrNull()?.isEmpty() == true) {
    val newSegments = chopLeadingEmptyBlock(segments)
    if (newSegments.isEmpty()) UrlPath.EMPTY else UrlPath(newSegments)
  }
  else
    this

fun UrlPath.chopTrailingEmptyBlock(): UrlPath =
  if (this !== UrlPath.EMPTY && hasEmptyTrailingBlock(segments)) {
    val newSegments = chopTrailingEmptyBlock(segments)
    if (newSegments.isEmpty()) UrlPath.EMPTY else UrlPath(newSegments)
  }
  else
    this

private fun chopLeadingEmptyBlock(segments: List<UrlPath.PathSegment>): List<UrlPath.PathSegment> =
  if (segments.firstOrNull()?.isEmpty() == true) segments.subList(1, segments.size) else segments

private fun chopTrailingEmptyBlock(segments: List<UrlPath.PathSegment>): List<UrlPath.PathSegment> =
  if (hasEmptyTrailingBlock(segments)) segments.subList(0, segments.size - 1) else segments

private fun hasEmptyTrailingBlock(segments: List<UrlPath.PathSegment>) =
  segments.size > 1 && segments.last().isEmpty()

interface UrlPathContextHolder {
  val urlPathContext: UrlPathContext
}