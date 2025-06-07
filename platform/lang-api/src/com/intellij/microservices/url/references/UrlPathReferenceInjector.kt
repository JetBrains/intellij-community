package com.intellij.microservices.url.references

import com.intellij.microservices.url.UrlPath
import com.intellij.microservices.url.parameters.QueryParameterNameReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import com.intellij.util.SmartList
import com.intellij.util.asSafely
import com.intellij.util.io.URLUtil.SCHEME_SEPARATOR

class UrlPathReferenceInjector<S> private constructor(
  val urlParser: UrlPksParser,
  private val splitRetrieval: (S) -> PartiallyKnownString?
) {

  var defaultRootContextProvider: (S) -> UrlPathContext = { UrlPathContext.emptyRoot() }

  private var pathSegmentHandler: PathSegmentHandler = DefaultExactPathSegmentHandler

  private var navigationHandler: ((UrlSegmentReference) -> Unit)? = null

  var alignToHost: (StringEntry) -> Pair<PsiElement, TextRange>? = { it.rangeAlignedToHost }

  fun withDefaultRootContextProviderFactory(factory: (S) -> UrlPathContext): UrlPathReferenceInjector<S> = apply {
    defaultRootContextProvider = factory
  }

  fun withSchemesSupport(schemes: List<String>): UrlPathReferenceInjector<S> = apply {
    val provider = UrlPathContext.supportingSchemes(schemes)
    defaultRootContextProvider = { provider }
  }

  fun withCustomHostAligner(converter: (StringEntry) -> Pair<PsiElement, TextRange>?): UrlPathReferenceInjector<S> = apply {
    this.alignToHost = converter
  }

  fun withPathSegmentHandler(handler: PathSegmentHandler): UrlPathReferenceInjector<S> = apply {
    pathSegmentHandler = handler
  }

  fun withCustomNavigationHandler(navigate: (UrlSegmentReference) -> Unit): UrlPathReferenceInjector<S> = apply {
    this.navigationHandler = navigate
  }

  companion object {
    @JvmStatic
    fun <S> forPartialStringFrom(urlParser: UrlPksParser, retrievalFun: (S) -> PartiallyKnownString?): UrlPathReferenceInjector<S> =
      UrlPathReferenceInjector(urlParser, retrievalFun)

    @JvmStatic
    fun <S> forPartialStringFrom(retrievalFun: (S) -> PartiallyKnownString?): UrlPathReferenceInjector<S> =
      forPartialStringFrom(UrlPksParser(), retrievalFun)

    @JvmStatic
    fun hasConsistentFullUrl(reference: Array<PsiReference>): Boolean {
      val schemeRange = reference.singleOrNull { it is SchemeReference }?.rangeInElement?.takeUnless { it.isEmpty } ?: return false
      val authRange = reference.singleOrNull { it is AuthorityReference }?.rangeInElement?.takeUnless { it.isEmpty } ?: return false
      val urlPathReferences = reference.filterIsInstance<UrlPathReference>()
      val baseRange = schemeRange.union(authRange)
      return urlPathReferences.none { it.rangeInElement.intersectsStrict(baseRange) }
    }
  }

  fun buildFullUrlReference(uElement: S, host: PsiElement): Array<PsiReference> {
    val pkwString = splitRetrieval(uElement) ?: return emptyArray()
    val hostTextRange = ElementManipulators.getValueTextRange(host)

    val parsedUrl = this.urlParser.parseFullUrl(pkwString)

    val scheme = parsedUrl.scheme ?: return emptyArray()
    return forbidExpensiveUrlContext {
      SmartList<PsiReference>().also { result ->
        val rangeInHost = scheme.getRangeInHost(host)
        if (rangeInHost != null && host is PsiLanguageInjectionHost) {
          val contextRoot = defaultRootContextProvider.invoke(uElement)
          result.add(SchemeReference(scheme.valueIfKnown, contextRoot.schemes, host, rangeInHost))
        }

        parsedUrl.authority?.let { authority ->
          val authorityReference = run authorityReference@{
            if (host !is PsiLanguageInjectionHost) return@authorityReference null
            val authorityRangeInHost = authority.getRangeInHost(host)
            when {
              authorityRangeInHost != null ->
                AuthorityReference(authority.valueIfKnown, host, hostTextRange.intersection(authorityRangeInHost) ?: hostTextRange,
                                   navigationHandler)

              // TODO: maybe it is better to make splitAtInKnown return empty segment in the last
              !scheme.valueIfKnown.isNullOrEmpty() && authority.valueIfKnown.isNullOrEmpty() && parsedUrl.slashesSplit.isEmpty() ->
                AuthorityReference(authority.valueIfKnown, host, TextRange(hostTextRange.endOffset, hostTextRange.endOffset),
                                   navigationHandler)

              else -> null
            }
          }

          if (authorityReference != null) {
            result.add(authorityReference)
          }

          val rootContextFromParsedBaseUrl = defaultRootContextProvider.invoke(uElement).applyFromParsed(parsedUrl, path = false)

          val urlPathReferences = if (parsedUrl.slashesSplit.isNotEmpty()) {
            buildReferencesForGivenSplit(parsedUrl.slashesSplit, rootContextFromParsedBaseUrl, uElement, host)
          }
          else result.lastOrNull()?.rangeInElement?.let { lastRef ->
            listOf(
              // a stub empty UrlPath at the end of authority reference
              UrlPathReference(
                rootContextFromParsedBaseUrl,
                host,
                TextRange(lastRef.endOffset, lastRef.endOffset),
                true,
                pathSegmentHandler = pathSegmentHandler,
                customNavigate = navigationHandler
              )
            )
          } ?: emptyList()

          result.addAll(urlPathReferences)
          result.addAll(buildQueryParamReferences(parsedUrl.queryParameters, urlPathReferences, uElement, host))
        }
      }.toTypedArray()
    }
  }

  fun buildReferences(source: S): MappedReferences {
    val pkwString = splitRetrieval(source) ?: return MappedReferences.Empty
    val parsedUrl = this.urlParser.parseUrlPath(pkwString)

    return object : MappedReferences {
      private var currentRootContextProvider: UrlPathContext? = null

      fun rootContext(): UrlPathContext = currentRootContextProvider ?: defaultRootContextProvider.invoke(source)

      override fun withRootContextProvider(rooContextProvider: UrlPathContext): MappedReferences = apply {
        currentRootContextProvider = rooContextProvider
      }

      override fun forPsiElement(host: PsiElement): Array<PsiReference> = forbidExpensiveUrlContext {
        val urlPathReferences = if (parsedUrl.slashesSplit.isNotEmpty()) {
          buildReferencesForGivenSplit(parsedUrl.slashesSplit, rootContext(), source, host)
        }
        else emptyList()

        val result = SmartList<PsiReference>()
        result.addAll(urlPathReferences)
        result.addAll(buildQueryParamReferences(parsedUrl.queryParameters, urlPathReferences, source, host))

        result.toTypedArray()
      }
    }
  }

  fun hasCompleteScheme(uElement: S): Boolean {
    val pks = this.splitRetrieval.invoke(uElement) ?: return false
    val parsed = urlParser.parseFullUrl(pks)
    val schemeValue = parsed.scheme?.valueIfKnown ?: return false
    return schemeValue.endsWith("://")
  }

  fun buildAbsoluteOrRelativeReferences(uElement: S, host: PsiElement): Array<PsiReference> {
    val fullReferences = buildFullUrlReference(uElement, host)
    if (hasConsistentFullUrl(fullReferences) || hasCompleteScheme(uElement)) {
      return fullReferences
    }

    val contextReferences = buildReferences(uElement).forPsiElement(host)
    return fullReferences + contextReferences
  }

  private fun buildQueryParamReferences(queryParameters: List<UrlPksParser.QueryParameter>,
                                        urlPathReferences: List<PsiReference>,
                                        source: S,
                                        host: PsiElement): List<PsiReference> {
    if (queryParameters.isEmpty() || host !is PsiLanguageInjectionHost) return emptyList()

    val result = SmartList<PsiReference>()
    val urlPathContext = urlPathReferences.lastOrNull()?.asSafely<UrlPathReference>()
                           ?.context ?: defaultRootContextProvider.invoke(source)

    queryParameters.mapNotNullTo(result) { queryParameter ->
      queryParameter.name.segments.asSequence()
        .filter { it.host == host }
        .firstNotNullOfOrNull(alignToHost)?.second?.let { nameRangeInHost ->
          QueryParameterNameReference(urlPathContext, host, nameRangeInHost)
        }
    }

    return result
  }

  fun toUrlPath(source: S): UrlPath {
    val sourceString = splitRetrieval(source) ?: return UrlPath.EMPTY
    val schemePosition = sourceString.findIndexOfInKnown(SCHEME_SEPARATOR)
    return if (schemePosition != -1)
      urlParser.parseFullUrl(sourceString).urlPath
    else
      urlParser.parseUrlPath(sourceString).urlPath
  }

  private fun buildReferencesForGivenSplit(slashesSplit: List<PartiallyKnownString>,
                                           rootContextProvider: UrlPathContext?,
                                           source: S,
                                           host: PsiElement): List<PsiReference> {
    val hostsUrlsMap = mapHostRangesToPathFirTree(this, slashesSplit)
    val currentHostUrls = hostsUrlsMap[host]
    if (currentHostUrls.isNullOrEmpty()) return emptyList()
    val maxSegments = currentHostUrls.asSequence().map { it.second.segments.size }.maxOrNull() ?: -1
    val valueTextRange = ElementManipulators.getValueTextRange(host)
    return currentHostUrls.map { (range, paths) ->
      val contextProvider = rootContextProvider ?: defaultRootContextProvider.invoke(source)
      UrlPathReference(contextProvider.subContext(paths),
                       host,
                       valueTextRange.intersection(range) ?: valueTextRange,
                       paths.segments.size == maxSegments,
                       pathSegmentHandler = pathSegmentHandler,
                       customNavigate = navigationHandler)
    }
  }

  interface MappedReferences {
    fun forPsiElement(host: PsiElement): Array<PsiReference>

    object Empty : MappedReferences {
      override fun forPsiElement(host: PsiElement): Array<PsiReference> = PsiReference.EMPTY_ARRAY
      override fun withRootContextProvider(rooContextProvider: UrlPathContext): MappedReferences = this
    }

    fun withRootContextProvider(rooContextProvider: UrlPathContext): MappedReferences
  }
}

private fun mapHostRangesToPathFirTree(injector: UrlPathReferenceInjector<*>,
                                       slashesSplit: List<PartiallyKnownString>): Map<PsiElement, List<Pair<TextRange, UrlPath>>> {
  val pathSegments = slashesSplit.map(injector.urlParser::pksPathSegment)
  return pathSegments.indices.flatMap { i ->
    val path = pathSegments.subList(0, i + 1)
    pksRangeInHostForFirTree(injector, slashesSplit[i]).map { (host, range) -> host to (range to UrlPath(path)) }
  }.groupBy({ it.first }, { it.second })
}

private fun pksRangeInHostForFirTree(injector: UrlPathReferenceInjector<*>, pks: PartiallyKnownString): Map<PsiElement, TextRange> =
  pks.segments.asSequence()
    .mapNotNull { injector.alignToHost(it) }
    .groupBy({ it.first }, { it.second })
    .mapNotNull { (host, ranges) ->
      val valueTextRange = ElementManipulators.getValueTextRange(host)
      val unitedRange = ranges.first()
      val asRangeInHost = valueTextRange.intersection(unitedRange)

      asRangeInHost?.let { host to asRangeInHost }
    }.toMap()
