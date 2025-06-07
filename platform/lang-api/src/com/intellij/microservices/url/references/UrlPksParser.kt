package com.intellij.microservices.url.references

import com.intellij.microservices.url.UrlPath
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.ReadActionCachedValue
import com.intellij.psi.util.SplitEscaper
import com.intellij.util.containers.tailOrEmpty

/**
 * A parser for extracting URL from a [PartiallyKnownString] and mapping URL parts to corresponding [PsiElement]s.
 *
 * Is configurable via fields to support framework-specific URL parts like Path Variables and Placeholders.
 */
class UrlPksParser @JvmOverloads constructor(
  var splitEscaper: (CharSequence, String) -> SplitEscaper = { _, _ -> SplitEscaper.AcceptAll },
  var customPathSegmentExtractor: (String) -> UrlPath.PathSegment? = ::extractSegmentLikeSpring,
  var parseQueryParameters: Boolean = true
) {
  /**
   * setting to `false` makes parser able to handle input without a scheme like `"localhost/some/path"`.
   * NOTE: in that case incomplete strings like `"loc"` could be treated as both host and scheme
   * which could lead to an incompatible scheme in produced results,
   * please check that [ParsedPksUrl.scheme] `!=` [ParsedPksUrl.authority] in that case before further processing
   */
  var shouldHaveScheme: Boolean = true

  @ConsistentCopyVisibility
  data class ParsedPksUrl internal constructor(val scheme: PartiallyKnownString?,
                                               val authority: PartiallyKnownString?,
                                               val slashesSplit: List<PartiallyKnownString>,
                                               val urlPath: UrlPath,
                                               val queryParameters: List<QueryParameter> = emptyList())

  @ConsistentCopyVisibility
  data class ParsedPksUrlPath internal constructor(val slashesSplit: List<PartiallyKnownString>,
                                                   val urlPath: UrlPath,
                                                   val queryParameters: List<QueryParameter> = emptyList())

  data class QueryParameter(val name: PartiallyKnownString, val value: PartiallyKnownString?)

  fun parseFullUrl(pkwString: PartiallyKnownString): ParsedPksUrl {
    val schemeSeparatorIndex = pkwString.findIndexOfInKnown(SCHEME_SEPARATOR)
    val firstSlashIndex = pkwString.findIndexOfInKnown("/")
    val (scheme, remaining) =
      when {
        schemeSeparatorIndex == -1 && firstSlashIndex == -1 ->
          if (shouldHaveScheme) {
            // full string is a Scheme if it is not blank
            val schemePart = if (pkwString.concatenationOfKnown.isBlank()) PartiallyKnownString.empty else pkwString
            schemePart to PartiallyKnownString.empty
          } 
          else
            pkwString to pkwString // we don't know if it is a scheme or host so treat it as both

        schemeSeparatorIndex >= 0 ->
          pkwString.splitAtInKnown(schemeSeparatorIndex + SCHEME_SEPARATOR.length) // canonical split by SCHEME_SEPARATOR

        else ->
          pkwString.splitAtInKnown(firstSlashIndex + 1).let { split ->
            if (shouldHaveScheme && split.second.valueIfKnown?.isEmpty() == true)
              split // probably incomplete scheme
            else
              PartiallyKnownString.empty to pkwString // everything is a path
          }
      }

    val slashesSplit = splitUrlPath(remaining) // always non-empty list

    if (!parseQueryParameters) {
      return if (shouldHaveScheme && scheme.valueIfKnown.isNullOrEmpty()) {
        ParsedPksUrl(scheme = scheme,
                     authority = null,
                     slashesSplit = slashesSplit,
                     urlPath = this.parseUrlPath(slashesSplit)
        )
      }
      else {
        val slashesSplitTail = slashesSplit.tailOrEmpty()
        ParsedPksUrl(scheme = scheme,
                     authority = slashesSplit.firstOrNull(),
                     slashesSplit = slashesSplitTail,
                     urlPath = this.parseUrlPath(slashesSplitTail)
        )
      }
    }

    val (pathSlashesSplit, queryString) = extractQueryPart(slashesSplit)
    val queryParameters = parseQueryParameters(queryString)

    return if (shouldHaveScheme && scheme.valueIfKnown.isNullOrEmpty())
      ParsedPksUrl(scheme = scheme,
                   authority = null,
                   slashesSplit = pathSlashesSplit,
                   urlPath = this.parseUrlPath(pathSlashesSplit),
                   queryParameters = queryParameters
      )
    else {
      val pathSlashesSplitTail = pathSlashesSplit.tailOrEmpty()
      ParsedPksUrl(scheme = scheme,
                   authority = pathSlashesSplit.firstOrNull(),
                   slashesSplit = pathSlashesSplitTail,
                   urlPath = this.parseUrlPath(pathSlashesSplitTail),
                   queryParameters = queryParameters
      )
    }
  }

  private fun extractQueryPart(slashesSplit: List<PartiallyKnownString>): Pair<List<PartiallyKnownString>, PartiallyKnownString?> {
    val lastNode = slashesSplit.lastOrNull() ?: return slashesSplit to null
    val qp = lastNode.findIndexOfInKnown(QUERY_SEPARATOR).takeIf { it != -1 } ?: return slashesSplit to null
    val (lastUpdated, queryString) = lastNode.splitAtInKnown(qp)

    return ArrayList(slashesSplit).apply { this[indices.last] = lastUpdated } to queryString
  }

  private fun parseQueryParameters(queryString: PartiallyKnownString?): List<QueryParameter> {
    return queryString
      ?.splitAtInKnown(QUERY_SEPARATOR.length)?.second
      ?.split(QUERY_PARAMS_SEPARATOR).orEmpty()
      .map { pair -> pair.split("=").let { QueryParameter(it[0], it.getOrNull(1)) } }
  }

  fun parseUrlPath(sourceString: PartiallyKnownString): ParsedPksUrlPath {
    val slashesSplit = splitUrlPath(sourceString)

    if (!parseQueryParameters) {
      return ParsedPksUrlPath(slashesSplit = slashesSplit,
                              urlPath = parseUrlPath(slashesSplit))
    }

    val (pathSlashesSplit, queryString) = extractQueryPart(slashesSplit)
    val queryParameters = parseQueryParameters(queryString)

    return ParsedPksUrlPath(slashesSplit = pathSlashesSplit,
                            urlPath = this.parseUrlPath(pathSlashesSplit),
                            queryParameters = queryParameters)
  }

  private val slashSplitCache = ReadActionCachedValue<MutableMap<PartiallyKnownString, List<PartiallyKnownString>>> { HashMap() }

  fun splitUrlPath(sourceString: PartiallyKnownString): List<PartiallyKnownString> {
    return slashSplitCache.getCachedOrEvaluate().getOrPut(sourceString) { sourceString.split("/", splitEscaper) }
  }

  private fun parseUrlPath(slashesSplit: List<PartiallyKnownString>): UrlPath = UrlPath(slashesSplit.map { pksPathSegment(it) })

  // mb refactor and make a method that will return the mapping of PathSegment to Pks and use this mapping instead of the split?
  fun pksPathSegment(pks: PartiallyKnownString): UrlPath.PathSegment =
    pks.valueIfKnown?.let { value -> customPathSegmentExtractor(value) ?: UrlPath.PathSegment.Exact(value) }
    ?: UrlPath.PathSegment.Undefined

  companion object {
    private const val SCHEME_SEPARATOR = "://"
    private const val QUERY_SEPARATOR = "?"
    private const val QUERY_PARAMS_SEPARATOR = "&"
  }
}