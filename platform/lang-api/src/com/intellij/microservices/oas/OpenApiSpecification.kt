package com.intellij.microservices.oas

class OpenApiSpecification(val paths: Collection<OasEndpointPath>,
                           val components: OasComponents? = null,
                           val tags: List<OasTag>? = null) {

  fun isEmpty(): Boolean = paths.iterator().hasNext()
}