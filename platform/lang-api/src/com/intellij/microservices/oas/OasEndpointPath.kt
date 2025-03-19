package com.intellij.microservices.oas

import com.intellij.microservices.url.UrlPath

class OasEndpointPath(val path: String,
                      val summary: String?,
                      val operations: Collection<OasOperation>) {
  val absolutePath: String = if (path.startsWith("/")) path else "/$path"

  class Builder(private val path: String) {

    constructor(urlPath: UrlPath) : this(urlPath.getPresentation(OPEN_API_PRESENTATION))

    var summary: String? = null
    var operations: Collection<OasOperation> = emptyList()

    @JvmOverloads
    fun build(block: (Builder.() -> Unit)? = null): OasEndpointPath {
      block?.invoke(this)
      return OasEndpointPath(path, summary, operations)
    }
  }
}