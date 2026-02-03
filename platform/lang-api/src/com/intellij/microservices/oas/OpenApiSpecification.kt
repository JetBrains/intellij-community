package com.intellij.microservices.oas

import com.intellij.microservices.mime.MimeTypes

class OpenApiSpecification(val paths: Collection<OasEndpointPath>,
                           val components: OasComponents? = null,
                           val tags: List<OasTag>? = null) {

  fun isEmpty(): Boolean = paths.iterator().hasNext()

  fun findSinglePathRequestBodyData(contentType: String = MimeTypes.APPLICATION_JSON): OasSchema? {
    return paths.asSequence()
      .flatMap { path ->
        path.operations.asSequence()
          .mapNotNull { it.requestBody?.content?.get(contentType) }
      }
      .firstOrNull()
  }
}