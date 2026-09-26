@file:JvmName("OasModelUtils")
package com.intellij.microservices.oas

import com.intellij.microservices.url.UrlPath

const val OPEN_API_UNKNOWN_SEGMENT: String = "<unknown>"

val OPEN_API_PRESENTATION: UrlPath.PathSegmentRenderer = object : UrlPath.PathSegmentRenderer {
  override fun visitVariable(variable: UrlPath.PathSegment.Variable): String = buildString {
    if (variable.variableName.isNullOrEmpty()) {
      append("{variable}")
    }
    else {
      append("{")
      append(variable.variableName)
      append("}")
    }
  }

  override fun visitUndefined(): String = OPEN_API_UNKNOWN_SEGMENT
}

fun getHttpMethodByName(methodName: String) : OasHttpMethod? {
  return OasHttpMethod.values().find {
    it.methodName.equals(methodName, ignoreCase = true)
  }
}