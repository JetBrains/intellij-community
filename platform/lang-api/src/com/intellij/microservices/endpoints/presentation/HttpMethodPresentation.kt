package com.intellij.microservices.endpoints.presentation

import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.editor.colors.TextAttributesKey
import javax.swing.Icon

class HttpMethodPresentation : PresentationData, EndpointMethodPresentation {

  override val endpointMethodOrder: Int
  override val endpointMethodPresentation: String?
  override val endpointMethods: List<String>

  @JvmOverloads
  constructor(httpUrl: String?,
              httpMethod: String?,
              definitionSource: String?,
              icon: Icon?,
              textAttributesKey: TextAttributesKey? = null) : super(httpUrl, definitionSource, icon, textAttributesKey) {
    val methodName = httpMethod?.uppercase()

    this.endpointMethodOrder = getHttpMethodOrder(methodName)
    this.endpointMethodPresentation = getHttpMethodPresentation(httpMethod)
    this.endpointMethods = methodName?.let { listOf(it) } ?: emptyList()
  }

  @JvmOverloads
  constructor(httpUrl: String?,
              httpMethods: List<String>,
              definitionSource: String?,
              icon: Icon?,
              textAttributesKey: TextAttributesKey? = null) : super(httpUrl, definitionSource, icon, textAttributesKey) {
    this.endpointMethodOrder = getHttpMethodOrder(httpMethods.joinToString(" ") { it.uppercase() })
    this.endpointMethodPresentation = getHttpMethodsPresentation(httpMethods)
    this.endpointMethods = httpMethods.map { it.uppercase() }
  }

  companion object {
    fun getHttpMethodPresentation(httpMethod: String?) : String {
      return httpMethod?.let { "[${httpMethod.uppercase()}]" } ?: ""
    }

    fun getHttpMethodsPresentation(httpMethods: Collection<String>) : String {
      if (httpMethods.isEmpty()) return ""
      return httpMethods.joinToString(prefix = "[", separator = "|", postfix = "]") { it.uppercase() }
    }

    fun getHttpMethodOrder(method: String?): Int {
      if (method == null) return 100

      return when (method) {
        "HEAD" -> 0
        "GET" -> 1
        "PUT" -> 2
        "POST" -> 3
        "PATCH" -> 4
        "DELETE" -> 5
        "OPTIONS" -> 6
        "TRACE" -> 7
        else -> 100
      }
    }
  }
}