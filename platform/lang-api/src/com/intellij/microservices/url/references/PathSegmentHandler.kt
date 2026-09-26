package com.intellij.microservices.url.references

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.microservices.endpoints.presentation.HttpMethodPresentation
import com.intellij.microservices.url.Authority
import com.intellij.microservices.url.UrlPath
import com.intellij.microservices.url.UrlResolveRequest
import com.intellij.microservices.url.UrlTargetInfo
import com.intellij.util.asSafely

interface PathSegmentHandler {
  fun render(segment: UrlPath.PathSegment): String

  fun getExactPrefix(path: UrlPath): List<UrlPath.PathSegment>

  fun createInsertHandler(variant: UrlTargetInfo, hasSomethingNext: Boolean): InsertHandler<LookupElement> = InsertHandler<LookupElement> { _, _ -> }

  fun createLookupElement(context: UrlResolveRequest,
                          exactPrefix: List<UrlPath.PathSegment>,
                          pathToComplete: List<UrlPath.PathSegment>,
                          variant: UrlTargetInfo,
                          prefix: String, suffix: String,
                          hasSomethingNext: Boolean): LookupElement? {
    val lookUp = exactPrefix.joinToString("/") { render(it) }
    if (lookUp.isEmpty()) return null
    val resultLookup = prefix + lookUp
    val followingPath = pathToComplete.joinToString("/") { render(it) }.removePrefix(lookUp).removePrefix(suffix)
    var lookup: LookupElement =
      LookupElementBuilder.create(UrlPathReference.UrlPathLookupObject(resultLookup), resultLookup + suffix)
        .withIcon(variant.icon)
        .withTailText(followingPath + " " + HttpMethodPresentation.getHttpMethodsPresentation(variant.methods))
        .withTypeText(variant.source)
        .withPsiElement(variant.documentationPsiElement)
        .withStrikeoutness(variant.isDeprecated)
        .withInsertHandler(createInsertHandler(variant, hasSomethingNext))

    if (context.authorityHint != null && variant.authorities.any { it.asSafely<Authority.Exact>()?.text == context.authorityHint }) {
      lookup = PrioritizedLookupElement.withPriority(lookup, 20.0)
    }

    return lookup
  }
}

internal object DefaultExactPathSegmentHandler : PathSegmentHandler {
  override fun render(segment: UrlPath.PathSegment): String =
    UrlPath.FULL_PATH_VARIABLE_PRESENTATION.patternMatch(segment)

  override fun getExactPrefix(path: UrlPath): List<UrlPath.PathSegment> =
    path.segments
      .takeWhile { it is UrlPath.PathSegment.Exact }
      .dropLastWhile { it.isEmpty() }
}