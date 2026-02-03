package com.intellij.microservices.oas

class OasOperation(val method: OasHttpMethod,
                   val tags: List<String>,
                   val description: String?,
                   val summary: String?,
                   val operationId: String?,
                   val isDeprecated: Boolean,
                   val parameters: Collection<OasParameter>,
                   val requestBody: OasRequestBody?,
                   val responses: Collection<OasResponse>) {

  class Builder(private val method: OasHttpMethod) {

    constructor(method: String) : this(getHttpMethodByName(method) ?: OasHttpMethod.GET)

    var tags: List<String> = emptyList()
    /**
     * A short summary of what the operation does.
     */
    var summary: String? = null

    /**
     * A verbose explanation of the operation behavior. Supports CommonMark syntax.
     */
    var description: String? = null
    var operationId: String? = null
    var isDeprecated: Boolean = false
    var parameters: List<OasParameter> = emptyList()
    var requestBody: OasRequestBody? = null
    var responses: List<OasResponse> = emptyList()

    @JvmOverloads
    fun build(block: (Builder.() -> Unit)? = null): OasOperation {
      block?.invoke(this)
      return OasOperation(method, tags, description, summary, operationId, isDeprecated, parameters, requestBody, responses)
    }
  }
}