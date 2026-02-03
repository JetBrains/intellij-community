package com.intellij.microservices.url

import java.util.regex.Pattern

object UrlConversionConstants {
  @JvmField
  val STANDARD_PATH_VARIABLE_NAME_PATTERN: Pattern = Pattern.compile("[\\w-]+", Pattern.UNICODE_CHARACTER_CLASS)

  @JvmField
  val SPRING_LIKE_PATH_VARIABLE_PATTERN: Pattern = Pattern.compile("([\\w-]+)\\s*:?(.+)?", Pattern.UNICODE_CHARACTER_CLASS)

  @JvmField
  val SPRING_LIKE_PATH_VARIABLE_BRACES: UrlSpecialSegmentMarker = UrlSpecialSegmentMarker("{", "}", SPRING_LIKE_PATH_VARIABLE_PATTERN)

  @JvmField
  val SPRING_LIKE_PLACEHOLDER_BRACES: UrlSpecialSegmentMarker = UrlSpecialSegmentMarker("\${", "}")
}