package com.intellij.microservices.url

import com.intellij.openapi.components.service
import com.intellij.psi.util.SplitEscaper
import org.jetbrains.annotations.ApiStatus

object PlaceholderSplitEscaper {
  fun create(begin: String, end: String, input: CharSequence, pattern: String): SplitEscaper {
    return create(listOf(UrlSpecialSegmentMarker(begin, end)), input, pattern)
  }

  fun create(braces: List<UrlSpecialSegmentMarker>, input: CharSequence, pattern: String): SplitEscaper {
    return service<PlaceholderSplitEscaperService>().create(braces, input, pattern)
  }
}

@ApiStatus.Internal
interface PlaceholderSplitEscaperService {
  fun create(braces: List<UrlSpecialSegmentMarker>, input: CharSequence, pattern: String): SplitEscaper
}