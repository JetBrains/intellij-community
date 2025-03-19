@file:JvmName("UrlExtractors")

package com.intellij.microservices.url.references

import com.intellij.microservices.url.*
import com.intellij.microservices.url.UrlPath.PathSegment
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.SplitEscaper
import com.intellij.util.SmartList

object DefaultFrameworkUrlPathSpecification : FrameworkUrlPathSpecification() {
  override fun getUrlPathContext(declaration: PsiElement): UrlPathContext = UrlPathContext.supportingSchemes(HTTP_METHODS).subContext(
    parser.parseUrlPath(PartiallyKnownString(
      ElementManipulators.getValueText(declaration), declaration, ElementManipulators.getValueTextRange(declaration)
    )).urlPath)

  override val parser: UrlPksParser = UrlPksParser().apply {
    splitEscaper = ::springLikePropertySplitEscaper
    shouldHaveScheme = false
    parseQueryParameters = false
  }
}

fun springLikePropertySplitEscaper(input: CharSequence, pattern: String): SplitEscaper =
  PlaceholderSplitEscaper.create(
    listOf(UrlConversionConstants.SPRING_LIKE_PLACEHOLDER_BRACES, UrlConversionConstants.SPRING_LIKE_PATH_VARIABLE_BRACES),
    input, pattern)

fun extractSegmentLikeSpring(segmentStr: String): PathSegment =
  extractPlaceholderAsUndefined(segmentStr, UrlConversionConstants.SPRING_LIKE_PLACEHOLDER_BRACES)
  ?: extractPathVariable(segmentStr, UrlConversionConstants.SPRING_LIKE_PATH_VARIABLE_BRACES)
  ?: extractAnyPathVariable(segmentStr, "*")
  ?: extractPlaceholder(segmentStr, "**")
  ?: PathSegment.Exact(segmentStr)

fun extractAnyPathVariable(segmentStr: String, anySequenceSymbol: String): PathSegment? =
  if (segmentStr != anySequenceSymbol) null else PathSegment.Variable(null)

fun extractPlaceholder(segmentStr: String, anySequenceSymbol: String): PathSegment? =
  if (segmentStr != anySequenceSymbol) null else PathSegment.Undefined

fun extractPlaceholderAsUndefined(segmentStr: String, wrap: UrlSpecialSegmentMarker): PathSegment? =
  if (wrap.matches(segmentStr)) PathSegment.Undefined else null

fun extractPathVariable(segmentStr: String, pathVariableWrap: UrlSpecialSegmentMarker): PathSegment? {
  val allVariables = pathVariableWrap.extractAll(segmentStr)
  if (allVariables.isEmpty()) return null
  allVariables.singleOrNull()?.let { (range, second) ->
    if (range.startOffset == 0 && range.endOffset == segmentStr.length)
      return PathSegment.Variable(second.value, second.regexGroups.getOrNull(2))
  }

  val resultSegments = SmartList<PathSegment>()
  var prevEnd = 0
  for ((varRange, v) in allVariables) {
    if (prevEnd != varRange.startOffset) {
      resultSegments.add(PathSegment.Exact(segmentStr.substring(prevEnd, varRange.startOffset)))
    }
    resultSegments.add(PathSegment.Variable(v.value, v.regexGroups.getOrNull(2)))
    prevEnd = varRange.endOffset
  }
  if (prevEnd != segmentStr.length) {
    resultSegments.add(PathSegment.Exact(segmentStr.substring(prevEnd, segmentStr.length)))
  }
  return PathSegment.Composite(resultSegments)
}