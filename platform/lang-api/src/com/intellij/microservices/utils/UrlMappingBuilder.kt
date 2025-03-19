package com.intellij.microservices.utils

class UrlMappingBuilder() {
  private val builder = StringBuilder()

  constructor(baseUrl: String?) : this() {
    if (baseUrl != null)
      builder.append(baseUrl)
  }

  fun appendSegment(part: String?): UrlMappingBuilder {
    if (part == null) return this

    if (builder.isNotEmpty() && !builder.endsWith("/") && !part.startsWith("/")) {
      builder.append('/')
    }
    if (builder.endsWith("/") && part.startsWith("/")) {
      builder.append(part, 1, part.length)
    }
    else {
      builder.append(part)
    }
    return this
  }

  fun buildOrNull(): String? {
    if (builder.isEmpty()) {
      return null
    }
    return builder.toString()
  }

  fun build(): String = builder.toString()
}