@file:JvmName("PlaceholderReplaceUtils")

package com.intellij.microservices.utils

import com.intellij.util.text.PlaceholderTextRanges

fun substituteUrlVariables(urlPattern: String,
                           prefix: String, suffix: String,
                           replacement: String): String {
  var url = urlPattern
  val ranges = PlaceholderTextRanges.getPlaceholderRanges(url, prefix, suffix, false, true)
  if (ranges.isEmpty()) {
    return url
  }

  val builder = StringBuilder(url.length)
  var offset = 0
  for (range in ranges) {
    builder.append(url, offset, range.startOffset - prefix.length)
    builder.append(replacement)
    offset = range.endOffset + suffix.length
  }
  builder.append(url.substring(offset))
  url = builder.toString()
  return url
}
