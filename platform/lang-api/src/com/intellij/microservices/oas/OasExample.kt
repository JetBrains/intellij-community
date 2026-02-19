package com.intellij.microservices.oas

class OasExample(
  val summary: String?,
  val description: String?,
  val value: OasExampleValue
) {
  class Builder(val value: OasExampleValue) {
    var summary: String? = null
    var description: String? = null

    @JvmOverloads
    fun build(block: (Builder.() -> Unit)? = null): OasExample {
      block?.invoke(this)
      return OasExample(summary, description, value)
    }
  }
}

sealed interface OasExampleValue

object OasNullValue : OasExampleValue

sealed class OasPrimitiveTypeValue<T>(val value: T) : OasExampleValue

class OasStringValue(value: String) : OasPrimitiveTypeValue<String>(value)
class OasNumberValue(value: Number) : OasPrimitiveTypeValue<Number>(value)
class OasBooleanValue(value: Boolean) : OasPrimitiveTypeValue<Boolean>(value)

class OasObjectValue(val properties: Map<String, OasExampleValue>) : OasExampleValue

class OasArrayValue(val items: List<OasExampleValue>) : OasExampleValue