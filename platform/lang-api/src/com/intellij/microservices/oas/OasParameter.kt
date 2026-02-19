package com.intellij.microservices.oas

class OasParameter(val name: String,
                   val inPlace: OasParameterIn,
                   val description: String?,
                   val isRequired: Boolean,
                   val isDeprecated: Boolean,
                   val schema: OasSchema?,
                   val style: OasParameterStyle?) {

  data class Builder(val name: String, private val inPlace: OasParameterIn) {
    var description: String? = null
    var isRequired: Boolean = false
    var isDeprecated: Boolean = false
    var schema: OasSchema? = null
    var style: OasParameterStyle? = null

    @JvmOverloads
    fun build(block: (Builder.() -> Unit)? = null): OasParameter {
      block?.invoke(this)
      return OasParameter(name, inPlace, description, isRequired, isDeprecated, schema, style)
    }
  }
}